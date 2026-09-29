import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'constant.dart';
import 'exception.dart';
import 'yaml.dart';

/// A plain VPN config that Pebble turns into a mihomo profile on import.
enum VpnConfigKind { none, wireGuard, openVpn }

/// Login for an OpenVPN server whose .ovpn has `auth-user-pass` without
/// inline credentials.
class OvpnCredentials {
  final String username;
  final String password;

  const OvpnCredentials(this.username, this.password);
}

/// Asks the user for OpenVPN credentials; null means the import was
/// cancelled. [server] is the host the .ovpn connects to.
typedef OvpnCredentialsRequest =
    Future<OvpnCredentials?> Function(String server);

/// Thrown when the user cancels the OpenVPN login dialog.
final class ImportCancelledException implements Exception {
  const ImportCancelledException();
}

const _maxConfigSize = 4 * 1024 * 1024;

String? _decodeText(Uint8List bytes) {
  if (bytes.isEmpty || bytes.length > _maxConfigSize) return null;
  try {
    var text = utf8.decode(bytes);
    if (text.startsWith('﻿')) text = text.substring(1);
    return text;
  } on FormatException {
    return null;
  }
}

Iterable<String> _trimmedLines(String text) =>
    const LineSplitter().convert(text).map((line) => line.trim());

bool looksLikeWireGuard(String text) {
  final lines = _trimmedLines(text).toList();
  return lines.any((l) => l.toLowerCase() == '[interface]') &&
      lines.any(
        (l) => l.replaceAll(' ', '').toLowerCase().startsWith('privatekey='),
      );
}

bool looksLikeOvpn(String text) {
  final lines = _trimmedLines(text).toList();
  final hasRemote = lines.any((l) => l.startsWith('remote '));
  return hasRemote &&
      (lines.any((l) => l == 'client' || l.startsWith('dev tun')) ||
          text.contains('<ca>'));
}

bool looksLikeClash(String text) => const LineSplitter()
    .convert(text)
    .any(
      (line) =>
          line.startsWith('proxies:') ||
          line.startsWith('proxy-providers:') ||
          line.startsWith('proxy-groups:'),
    );

VpnConfigKind detectVpnConfig(String text, {String? fileName}) {
  final body = text.trim();
  if (body.isEmpty) return VpnConfigKind.none;
  if (looksLikeClash(body)) return VpnConfigKind.none;
  final name = fileName?.toLowerCase() ?? '';
  if (looksLikeWireGuard(body)) return VpnConfigKind.wireGuard;
  if (name.endsWith('.ovpn') || looksLikeOvpn(body)) {
    return VpnConfigKind.openVpn;
  }
  return VpnConfigKind.none;
}

/// A profile name from a file name: drops the directory and extension.
String? nameFromFileName(String? fileName) {
  if (fileName == null) return null;
  var name = fileName.split(RegExp(r'[/\\]')).last;
  final dot = name.lastIndexOf('.');
  if (dot > 0) name = name.substring(0, dot);
  name = name.trim();
  return name.isEmpty ? null : name;
}

/// Converts WireGuard/AmneziaWG `.conf` and OpenVPN `.ovpn` files into a
/// mihomo profile; anything else is returned unchanged.
///
/// [previousProfile] is the profile's current YAML, if any: when a
/// subscription URL serves an .ovpn that needs a login, the credentials the
/// user entered on import are taken from it.
Future<Uint8List> convertVpnConfig(
  Uint8List bytes, {
  String? fileName,
  OvpnCredentialsRequest? askCredentials,
  String? previousProfile,
}) async {
  final text = _decodeText(bytes);
  if (text == null) return bytes;
  final name = nameFromFileName(fileName);
  switch (detectVpnConfig(text, fileName: fileName)) {
    case VpnConfigKind.none:
      return bytes;
    case VpnConfigKind.wireGuard:
      return _utf8(profileFromWireGuard(text, name: name));
    case VpnConfigKind.openVpn:
      final ovpn = OvpnFile.parse(text);
      OvpnCredentials? credentials;
      if (ovpn.needsPassword) {
        credentials =
            credentialsFromProfile(previousProfile) ??
            (askCredentials == null
                ? null
                : await askCredentials(ovpn.remotes.first.host));
        if (credentials == null) {
          if (askCredentials != null) throw const ImportCancelledException();
          throw const MessageException(
            'This OpenVPN server needs a username and password',
          );
        }
      }
      return _utf8(profileFromOvpn(ovpn, name: name, credentials: credentials));
  }
}

Uint8List _utf8(String text) => Uint8List.fromList(utf8.encode(text));

/// Reads the OpenVPN username/password back out of a profile Pebble wrote.
OvpnCredentials? credentialsFromProfile(String? profile) {
  if (profile == null || !profile.contains('openvpn')) return null;
  String? field(String key) {
    final match = RegExp(
      '^\\s*$key:\\s*(.+?)\\s*\$',
      multiLine: true,
    ).firstMatch(profile);
    if (match == null) return null;
    return _unquoteYamlScalar(match.group(1)!);
  }

  final username = field('username');
  if (username == null || username.isEmpty) return null;
  return OvpnCredentials(username, field('password') ?? '');
}

String _unquoteYamlScalar(String value) {
  if (value.length >= 2 && value.startsWith('"') && value.endsWith('"')) {
    try {
      return jsonDecode(value) as String;
    } on FormatException {
      return value.substring(1, value.length - 1);
    }
  }
  if (value.length >= 2 && value.startsWith("'") && value.endsWith("'")) {
    return value.substring(1, value.length - 1).replaceAll("''", "'");
  }
  return value;
}

// WireGuard ------------------------------------------------------------------

class WireGuardPeer {
  String publicKey = '';
  String presharedKey = '';
  String endpoint = '';
  final List<String> allowedIps = [];
  int keepalive = 0;
}

class WireGuardConfig {
  String privateKey = '';
  final List<String> addresses = [];
  final List<String> dns = [];
  int mtu = 0;
  final Map<String, String> amnezia = {};
  final List<WireGuardPeer> peers = [];

  static const amneziaKeys = {
    'jc', 'jmin', 'jmax', 's1', 's2', 's3', 's4', 'h1', 'h2', 'h3', 'h4', //
    'i1', 'i2', 'i3', 'i4', 'i5', 'j1', 'j2', 'j3', 'itime',
  };

  static List<String> _splitList(String value) => value
      .split(',')
      .map((item) => item.trim())
      .where((item) => item.isNotEmpty)
      .toList();

  factory WireGuardConfig.parse(String text) {
    final config = WireGuardConfig._();
    var section = '';
    for (var line in const LineSplitter().convert(text)) {
      final comment = line.indexOf(RegExp('[#;]'));
      if (comment >= 0) line = line.substring(0, comment);
      line = line.trim();
      if (line.isEmpty) continue;
      if (line.startsWith('[')) {
        section = line.replaceAll(RegExp(r'[\[\]\s]'), '').toLowerCase();
        if (section == 'peer') config.peers.add(WireGuardPeer());
        continue;
      }
      final eq = line.indexOf('=');
      if (eq < 0) throw MessageException('Invalid WireGuard line: $line');
      final key = line.substring(0, eq).trim().toLowerCase();
      final value = line.substring(eq + 1).trim();
      if (section == 'interface') {
        switch (key) {
          case 'privatekey':
            config.privateKey = value;
          case 'address':
            config.addresses.addAll(_splitList(value));
          case 'dns':
            // Search domains are not supported by mihomo's outbound.
            config.dns.addAll(
              _splitList(
                value,
              ).where((item) => InternetAddress.tryParse(item) != null),
            );
          case 'mtu':
            config.mtu = int.tryParse(value) ?? 0;
          default:
            if (amneziaKeys.contains(key)) config.amnezia[key] = value;
        }
      } else if (section == 'peer') {
        final peer = config.peers.last;
        switch (key) {
          case 'publickey':
            peer.publicKey = value;
          case 'presharedkey':
            peer.presharedKey = value;
          case 'endpoint':
            peer.endpoint = value;
          case 'allowedips':
            peer.allowedIps.addAll(_splitList(value));
          case 'persistentkeepalive':
            peer.keepalive = int.tryParse(value) ?? 0;
        }
      }
    }
    config._validate();
    return config;
  }

  WireGuardConfig._();

  void _validate() {
    if (privateKey.isEmpty) {
      throw const MessageException('WireGuard: missing [Interface] PrivateKey');
    }
    _checkKey(privateKey, 'PrivateKey');
    if (addresses.isEmpty) {
      throw const MessageException('WireGuard: missing [Interface] Address');
    }
    if (peers.isEmpty) {
      throw const MessageException('WireGuard: no [Peer] section');
    }
    for (final (index, peer) in peers.indexed) {
      if (peer.publicKey.isEmpty) {
        throw MessageException('WireGuard: peer ${index + 1} has no PublicKey');
      }
      _checkKey(peer.publicKey, 'PublicKey');
      if (peer.presharedKey.isNotEmpty) {
        _checkKey(peer.presharedKey, 'PresharedKey');
      }
      if (peer.endpoint.isEmpty) {
        throw MessageException('WireGuard: peer ${index + 1} has no Endpoint');
      }
    }
  }

  static void _checkKey(String key, String field) {
    try {
      if (base64.decode(key).length == 32) return;
    } on FormatException {
      // Reported below.
    }
    throw MessageException('WireGuard: invalid $field');
  }
}

/// Splits `host:port` and `[v6]:port`.
(String, int) splitHostPort(String value) {
  final String host;
  final String port;
  if (value.startsWith('[')) {
    final end = value.indexOf(']');
    if (end < 0 || end + 1 >= value.length || value[end + 1] != ':') {
      throw MessageException('Invalid endpoint: $value');
    }
    host = value.substring(1, end);
    port = value.substring(end + 2);
  } else {
    final colon = value.lastIndexOf(':');
    if (colon <= 0) throw MessageException('Invalid endpoint: $value');
    host = value.substring(0, colon);
    port = value.substring(colon + 1);
  }
  final number = int.tryParse(port);
  if (number == null || number <= 0 || number > 65535) {
    throw MessageException('Invalid endpoint port: $value');
  }
  return (host, number);
}

Map<String, Object> wireGuardToProxy(WireGuardConfig config, {String? name}) {
  final first = config.peers.first;
  final (host, port) = splitHostPort(first.endpoint);
  final proxy = <String, Object>{
    'name': name ?? 'WireGuard $host',
    'type': 'wireguard',
    'private-key': config.privateKey,
  };
  for (final address in config.addresses) {
    final ip = address.split('/').first;
    final parsed = InternetAddress.tryParse(ip);
    if (parsed == null) {
      throw MessageException('WireGuard: invalid Address $address');
    }
    if (parsed.type == InternetAddressType.IPv4) {
      proxy.putIfAbsent('ip', () => ip);
    } else {
      proxy.putIfAbsent('ipv6', () => ip);
    }
  }
  List<String> allowedIps(WireGuardPeer peer) =>
      peer.allowedIps.isEmpty ? const ['0.0.0.0/0', '::/0'] : peer.allowedIps;
  if (config.peers.length == 1) {
    proxy['server'] = host;
    proxy['port'] = port;
    proxy['public-key'] = first.publicKey;
    if (first.presharedKey.isNotEmpty) {
      proxy['pre-shared-key'] = first.presharedKey;
    }
    proxy['allowed-ips'] = allowedIps(first);
  } else {
    proxy['peers'] = [
      for (final peer in config.peers)
        () {
          final (peerHost, peerPort) = splitHostPort(peer.endpoint);
          return <String, Object>{
            'server': peerHost,
            'port': peerPort,
            'public-key': peer.publicKey,
            if (peer.presharedKey.isNotEmpty)
              'pre-shared-key': peer.presharedKey,
            'allowed-ips': allowedIps(peer),
          };
        }(),
    ];
  }
  final keepalive = config.peers
      .map((peer) => peer.keepalive)
      .fold(0, (a, b) => a > b ? a : b);
  if (keepalive > 0) proxy['persistent-keepalive'] = keepalive;
  if (config.mtu > 0) proxy['mtu'] = config.mtu;
  proxy['udp'] = true;
  if (config.dns.isNotEmpty) {
    proxy['remote-dns-resolve'] = true;
    proxy['dns'] = config.dns;
  }
  if (config.amnezia.isNotEmpty) {
    final options = <String, Object>{};
    for (final key in WireGuardConfig.amneziaKeys) {
      final value = config.amnezia[key];
      if (value == null) continue;
      // jc/jmin/jmax/s1-s4/itime are integers in mihomo; h*, i* and j* are
      // strings (ranges and packet templates).
      final numeric =
          const {'itime', 'jc', 'jmin', 'jmax'}.contains(key) ||
          key.startsWith('s');
      options[key] = numeric ? int.tryParse(value) ?? value : value;
    }
    proxy['amnezia-wg-option'] = options;
  }
  return proxy;
}

String profileFromWireGuard(String text, {String? name}) => profileFromProxies([
  wireGuardToProxy(WireGuardConfig.parse(text), name: name),
]);

// OpenVPN --------------------------------------------------------------------

class OvpnRemote {
  final String host;
  final int port;
  final String proto;

  const OvpnRemote(this.host, this.port, this.proto);
}

class OvpnFile {
  /// Directive name -> arguments of its last occurrence.
  final Map<String, List<String>> directives = {};
  final List<List<String>> _remoteArgs = [];

  /// Inline blocks such as `<ca>`, `<cert>`, `<auth-user-pass>`.
  final Map<String, String> blocks = {};

  OvpnFile._();

  factory OvpnFile.parse(String text) {
    final file = OvpnFile._();
    String? block;
    final body = StringBuffer();
    for (final raw in const LineSplitter().convert(text)) {
      final line = raw.trim();
      if (block != null) {
        if (line == '</$block>') {
          file.blocks[block] = '${body.toString().trim()}\n';
          block = null;
          body.clear();
        } else {
          body.writeln(line);
        }
        continue;
      }
      if (line.isEmpty || line.startsWith('#') || line.startsWith(';')) {
        continue;
      }
      if (line.startsWith('<') &&
          line.endsWith('>') &&
          !line.startsWith('</')) {
        block = line.substring(1, line.length - 1).toLowerCase();
        continue;
      }
      final fields = _splitArgs(line);
      final name = fields.first.toLowerCase();
      final args = fields.sublist(1);
      if (name == 'remote' && args.isNotEmpty) file._remoteArgs.add(args);
      file.directives[name] = args;
    }
    if (block != null) {
      throw MessageException('OpenVPN: unterminated <$block> block');
    }
    if (file._remoteArgs.isEmpty) {
      throw const MessageException('OpenVPN: no remote server in the file');
    }
    return file;
  }

  static List<String> _splitArgs(String line) {
    final out = <String>[];
    final current = StringBuffer();
    var quoted = false;
    for (final char in line.split('')) {
      if (char == '"') {
        quoted = !quoted;
      } else if ((char == ' ' || char == '\t') && !quoted) {
        if (current.isNotEmpty) {
          out.add(current.toString());
          current.clear();
        }
      } else {
        current.write(char);
      }
    }
    if (current.isNotEmpty) out.add(current.toString());
    return out;
  }

  bool has(String name) => directives.containsKey(name);

  String arg(String name, [int index = 0]) {
    final args = directives[name];
    return args != null && index < args.length ? args[index] : '';
  }

  /// Every `remote`, deduplicated, with `port`/`proto` defaults applied.
  List<OvpnRemote> get remotes {
    final seen = <String>{};
    final out = <OvpnRemote>[];
    for (final args in _remoteArgs) {
      var port = int.tryParse(arg('port')) ?? 1194;
      if (args.length > 1) port = int.tryParse(args[1]) ?? port;
      var proto = arg('proto').toLowerCase();
      if (args.length > 2) proto = args[2].toLowerCase();
      proto = proto.startsWith('tcp') ? 'tcp' : 'udp';
      if (seen.add('${args[0]}:$port/$proto')) {
        out.add(OvpnRemote(args[0], port, proto));
      }
    }
    return out;
  }

  /// Username and password of an inline `<auth-user-pass>` block.
  OvpnCredentials? get inlineCredentials {
    final block = blocks['auth-user-pass'];
    if (block == null) return null;
    final lines = const LineSplitter().convert(block.trim());
    final user = lines.isEmpty ? '' : lines.first.trim();
    if (user.isEmpty) return null;
    return OvpnCredentials(user, lines.length > 1 ? lines[1].trim() : '');
  }

  /// True when the server asks for a login the file does not contain.
  /// `auth-user-pass <file>` counts: Pebble cannot read that file.
  bool get needsPassword => inlineCredentials == null && has('auth-user-pass');
}

Map<String, Object> ovpnToProxy(
  OvpnFile file,
  OvpnRemote remote, {
  required String name,
  OvpnCredentials? credentials,
}) {
  final ca = file.blocks['ca'];
  if (ca == null) {
    throw const MessageException(
      'OpenVPN: the .ovpn file has no inline <ca> block',
    );
  }
  final proxy = <String, Object>{
    'name': name,
    'type': 'openvpn',
    'server': remote.host,
    'port': remote.port,
    'proto': remote.proto,
    'udp': true,
    'ca': ca,
  };
  for (final key in const ['cert', 'key', 'tls-crypt', 'tls-crypt-v2']) {
    final value = file.blocks[key];
    if (value != null) proxy[key] = value;
  }
  final tlsAuth = file.blocks['tls-auth'];
  if (tlsAuth != null) {
    proxy['tls-auth'] = tlsAuth;
    var direction = file.arg('key-direction');
    if (direction.isEmpty) direction = file.arg('tls-auth', 1);
    if (direction.isNotEmpty) proxy['key-direction'] = direction;
  }
  final cipher = file.arg('cipher');
  if (cipher.isNotEmpty) proxy['cipher'] = cipher;
  var dataCiphers = file.arg('data-ciphers');
  if (dataCiphers.isEmpty) dataCiphers = file.arg('ncp-ciphers');
  if (dataCiphers.isNotEmpty) proxy['data-ciphers'] = dataCiphers.split(':');
  final fallback = file.arg('data-ciphers-fallback');
  if (fallback.isNotEmpty) proxy['data-ciphers-fallback'] = fallback;
  final auth = file.arg('auth');
  if (auth.isNotEmpty) proxy['auth'] = auth;
  if (file.has('comp-lzo')) {
    final value = file.arg('comp-lzo');
    proxy['comp-lzo'] = value.isEmpty ? 'adaptive' : value;
  }
  void putInt(String key, String value) {
    final number = int.tryParse(value);
    if (number != null) proxy[key] = number;
  }

  if (file.has('keepalive')) {
    putInt('ping', file.arg('keepalive'));
    putInt('ping-restart', file.arg('keepalive', 1));
  }
  putInt('ping', file.arg('ping'));
  putInt('ping-restart', file.arg('ping-restart'));
  putInt('mtu', file.arg('tun-mtu'));
  final login = credentials ?? file.inlineCredentials;
  if (file.has('auth-user-pass') || login != null) {
    if (login == null) {
      throw const MessageException(
        'This OpenVPN server needs a username and password',
      );
    }
    proxy['username'] = login.username;
    proxy['password'] = login.password;
  }
  return proxy;
}

String profileFromOvpn(
  OvpnFile file, {
  String? name,
  OvpnCredentials? credentials,
}) {
  final remotes = file.remotes;
  return profileFromProxies([
    for (final remote in remotes)
      ovpnToProxy(
        file,
        remote,
        name: remotes.length == 1 && name != null
            ? name
            : '${name ?? 'OpenVPN'} ${remote.host}',
        credentials: credentials,
      ),
  ]);
}

// Profile --------------------------------------------------------------------

/// Wraps proxies in a ready-to-use profile: a "Proxy" selector (plus an
/// "Auto" url-test group when there are several) and a catch-all rule.
String profileFromProxies(List<Map<String, Object>> proxies) {
  final names = <String>[];
  final seen = <String, int>{};
  for (final proxy in proxies) {
    var name = (proxy['name'] as String?) ?? '${proxy['type']}';
    final count = seen[name] ?? 0;
    seen[name] = count + 1;
    // mihomo rejects duplicate names.
    if (count > 0) name = '$name (${count + 1})';
    proxy['name'] = name;
    names.add(name);
  }
  final groups = names.length == 1
      ? [
          {'name': 'Proxy', 'type': 'select', 'proxies': names},
        ]
      : [
          {
            'name': 'Proxy',
            'type': 'select',
            'proxies': ['Auto', ...names],
          },
          {
            'name': 'Auto',
            'type': 'url-test',
            'proxies': names,
            'url': defaultTestUrl,
            'interval': 300,
            'tolerance': 50,
          },
        ];
  return yaml.encode({
    'mode': 'rule',
    'proxies': proxies,
    'proxy-groups': groups,
    'rules': ['MATCH,Proxy'],
  });
}
