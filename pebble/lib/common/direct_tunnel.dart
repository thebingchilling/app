import 'dart:convert';
import 'dart:typed_data';

import 'exception.dart';

/// Tunnels that run on their own engine instead of mihomo: WireGuard on the
/// official WireGuard library, AmneziaWG on Amnezia's fork of it, OpenVPN on
/// OpenVPN 2.6 as built by OpenVPN for Android.
enum DirectTunnelType {
  wireguard('WireGuard'),
  amneziawg('AmneziaWG'),
  openvpn('OpenVPN');

  final String label;

  const DirectTunnelType(this.label);
}

/// A `.conf` or `.ovpn` file kept word for word, plus the OpenVPN login the
/// user entered on import when the file asks for one.
class DirectTunnel {
  final DirectTunnelType type;
  final String config;
  final String? username;
  final String? password;

  const DirectTunnel({
    required this.type,
    required this.config,
    this.username,
    this.password,
  });

  bool get hasLogin => username != null && username!.isNotEmpty;

  Map<String, Object> toJson() => {
    'type': type.name,
    'config': config,
    'username': ?username,
    'password': ?password,
  };

  static DirectTunnel? fromJson(Object? json) {
    if (json is! Map) return null;
    final type = DirectTunnelType.values
        .where((value) => value.name == json['type'])
        .firstOrNull;
    final config = json['config'];
    if (type == null || config is! String) return null;
    return DirectTunnel(
      type: type,
      config: config,
      username: json['username'] as String?,
      password: json['password'] as String?,
    );
  }
}

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

/// The top-level key of a direct-tunnel profile. mihomo ignores keys it does
/// not know, and the Android service reads this one line to start the engine.
const directTunnelKey = 'x-pebble-direct';

const _maxConfigSize = 4 * 1024 * 1024;

/// AmneziaWG's additions to WireGuard's `[Interface]` section.
const _amneziaKeys = {
  'jc', 'jmin', 'jmax', 's1', 's2', 's3', 's4', 'h1', 'h2', 'h3', 'h4', //
  'i1', 'i2', 'i3', 'i4', 'i5', 'j1', 'j2', 'j3', 'itime',
};

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

Iterable<String> _lines(String text) =>
    const LineSplitter().convert(text).map((line) => line.trim());

bool _looksLikeClash(String text) => const LineSplitter()
    .convert(text)
    .any(
      (line) =>
          line.startsWith('proxies:') ||
          line.startsWith('proxy-providers:') ||
          line.startsWith('proxy-groups:') ||
          line.startsWith('$directTunnelKey:'),
    );

bool _looksLikeWireGuard(String text) {
  final lines = _lines(text).toList();
  return lines.any((line) => line.toLowerCase() == '[interface]') &&
      lines.any(
        (line) =>
            line.replaceAll(' ', '').toLowerCase().startsWith('privatekey='),
      );
}

bool _isAmneziaWg(String text) {
  var inInterface = false;
  for (final line in _lines(text)) {
    if (line.startsWith('[')) {
      inInterface = line.toLowerCase() == '[interface]';
      continue;
    }
    final eq = line.indexOf('=');
    if (!inInterface || eq <= 0) continue;
    if (_amneziaKeys.contains(line.substring(0, eq).trim().toLowerCase())) {
      return true;
    }
  }
  return false;
}

bool _looksLikeOvpn(String text) {
  final lines = _lines(text).toList();
  return lines.any((line) => line.startsWith('remote ')) &&
      (lines.any((line) => line == 'client' || line.startsWith('dev ')) ||
          text.contains('<ca>'));
}

/// The kind of tunnel [text] is, or null for anything else (Clash profiles
/// included).
DirectTunnelType? detectDirectTunnel(String text, {String? fileName}) {
  final body = text.trim();
  if (body.isEmpty || _looksLikeClash(body)) return null;
  if (_looksLikeWireGuard(body)) {
    return _isAmneziaWg(body)
        ? DirectTunnelType.amneziawg
        : DirectTunnelType.wireguard;
  }
  final name = fileName?.toLowerCase() ?? '';
  if (_looksLikeOvpn(body) ||
      (name.endsWith('.ovpn') && body.contains('remote'))) {
    return DirectTunnelType.openvpn;
  }
  return null;
}

/// OpenVPN directives with their arguments, skipping comments and inline
/// `<tag>` blocks.
Iterable<List<String>> _ovpnDirectives(String config) sync* {
  String? block;
  for (final line in _lines(config)) {
    if (block != null) {
      if (line == '</$block>') block = null;
      continue;
    }
    final open = RegExp(r'^<([a-z0-9-]+)>$').firstMatch(line);
    if (open != null) {
      block = open.group(1);
      continue;
    }
    if (line.isEmpty || line.startsWith('#') || line.startsWith(';')) continue;
    yield line.split(RegExp(r'\s+'));
  }
}

/// True when the server wants a username and password that the file does
/// not carry itself.
bool ovpnNeedsLogin(String config) {
  if (config.contains('<auth-user-pass>')) return false;
  return _ovpnDirectives(config).any((args) => args.first == 'auth-user-pass');
}

/// The first `remote` host of an .ovpn, for the login dialog.
String ovpnServer(String config) {
  for (final args in _ovpnDirectives(config)) {
    if (args.first == 'remote' && args.length > 1) return args[1];
  }
  return 'OpenVPN';
}

/// Android's VPN API only carries IP packets, so a layer-2 (`dev tap`)
/// server cannot work; say so on import instead of failing on connect.
void _checkOvpnSupported(String config) {
  for (final args in _ovpnDirectives(config)) {
    if (args.first == 'dev' && args.length > 1 && args[1].startsWith('tap')) {
      throw const MessageException(
        'This OpenVPN server uses "dev tap" (layer 2). Android VPNs only '
        'carry IP traffic, so only "dev tun" servers work.',
      );
    }
  }
}

/// The profile Pebble saves for a direct tunnel. mihomo gets an empty
/// profile that sends everything DIRECT, which keeps FlClash's pages working
/// while the tunnel's own engine carries the traffic.
String directTunnelProfile(DirectTunnel tunnel) {
  return '# Pebble direct ${tunnel.type.label} tunnel. The original file is '
      'kept in $directTunnelKey\n'
      '# and runs on its own engine; mihomo only sees the rules below.\n'
      '$directTunnelKey: ${jsonEncode(tunnel.toJson())}\n'
      'proxies: []\n'
      'rules:\n'
      '  - MATCH,DIRECT\n';
}

/// Reads the direct tunnel back out of a profile Pebble wrote, or null for
/// any other profile.
DirectTunnel? readDirectTunnel(String? profile) {
  if (profile == null) return null;
  for (final line in const LineSplitter().convert(profile)) {
    if (!line.startsWith('$directTunnelKey:')) continue;
    try {
      return DirectTunnel.fromJson(
        jsonDecode(line.substring(directTunnelKey.length + 1)),
      );
    } on FormatException {
      return null;
    }
  }
  return null;
}

/// Turns a WireGuard/AmneziaWG `.conf` or an OpenVPN `.ovpn` into a
/// direct-tunnel profile; anything else is returned unchanged.
///
/// [previousProfile] is the profile's current file, if any: when a
/// subscription URL serves an .ovpn that needs a login, the login entered on
/// import is kept.
Future<Uint8List> convertDirectTunnel(
  Uint8List bytes, {
  String? fileName,
  OvpnCredentialsRequest? askCredentials,
  String? previousProfile,
}) async {
  final text = _decodeText(bytes);
  if (text == null) return bytes;
  final type = detectDirectTunnel(text, fileName: fileName);
  if (type == null) return bytes;
  var tunnel = DirectTunnel(type: type, config: text);
  if (type == DirectTunnelType.openvpn) {
    _checkOvpnSupported(text);
    if (ovpnNeedsLogin(text)) {
      final previous = readDirectTunnel(previousProfile);
      final credentials = previous != null && previous.hasLogin
          ? OvpnCredentials(previous.username!, previous.password ?? '')
          : await askCredentials?.call(ovpnServer(text));
      if (credentials == null) {
        if (askCredentials != null) throw const ImportCancelledException();
        throw const MessageException(
          'This OpenVPN server needs a username and password',
        );
      }
      tunnel = DirectTunnel(
        type: type,
        config: text,
        username: credentials.username,
        password: credentials.password,
      );
    }
  }
  return Uint8List.fromList(utf8.encode(directTunnelProfile(tunnel)));
}
