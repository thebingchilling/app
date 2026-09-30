import 'dart:convert';
import 'dart:typed_data';

import 'package:fl_clash/common/common.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:yaml/yaml.dart';

const _key1 = 'yAnz5TF+lXXJte14tji3zlMNq+hd2rYUIgJBgB3fBmk=';
const _key2 = 'xTIBA5rboUvnH4htodjb6e697QjLERt1NAB4mZqp8Dg=';
const _key3 = 'TrMvSoP4jYQlY6RIzBgbssQqY3vxI2Pi+y71lOWWXX0=';

const _wireGuard =
    '''
[Interface]
PrivateKey = $_key1
Address = 10.8.0.2/32, fd00::2/128
DNS = 1.1.1.1, example.internal
MTU = 1380
# AmneziaWG
Jc = 4
Jmin = 40
Jmax = 70
S1 = 0
H1 = 1

[Peer]
PublicKey = $_key2
PresharedKey = $_key3
Endpoint = vpn.example.com:51820
AllowedIPs = 0.0.0.0/0, ::/0
PersistentKeepalive = 25
''';

const _ca = '-----BEGIN CERTIFICATE-----\nMIIB\n-----END CERTIFICATE-----';

const _ovpn =
    '''
client
dev tun
proto udp
remote nl1.example.net 1194
remote nl2.example.net 443 tcp
cipher AES-256-GCM
data-ciphers AES-256-GCM:AES-128-GCM
auth SHA512
auth-user-pass
keepalive 10 60
<ca>
$_ca
</ca>
<tls-crypt>
-----BEGIN OpenVPN Static key V1-----
abcd
-----END OpenVPN Static key V1-----
</tls-crypt>
''';

YamlMap _load(String text) => loadYaml(text) as YamlMap;

Uint8List _bytes(String text) => Uint8List.fromList(utf8.encode(text));

void main() {
  group('detectVpnConfig', () {
    test('recognises WireGuard, OpenVPN and leaves Clash YAML alone', () {
      expect(detectVpnConfig(_wireGuard), VpnConfigKind.wireGuard);
      expect(detectVpnConfig(_ovpn), VpnConfigKind.openVpn);
      expect(
        detectVpnConfig('remote a.example 1194', fileName: 'x.ovpn'),
        VpnConfigKind.openVpn,
      );
      expect(detectVpnConfig('proxies: []\nrules: []'), VpnConfigKind.none);
      // An imported .ovpn keeps its file name as label; editing the
      // converted YAML must not be treated as OpenVPN again.
      expect(
        detectVpnConfig('mode: rule\nproxies: []', fileName: 'x.ovpn'),
        VpnConfigKind.none,
      );
      expect(detectVpnConfig('https://example.com/sub'), VpnConfigKind.none);
    });
  });

  group('WireGuard', () {
    test('converts a .conf into a single-proxy profile', () {
      final doc = _load(profileFromWireGuard(_wireGuard, name: 'home'));
      final proxy = (doc['proxies'] as YamlList).single as YamlMap;
      expect(proxy['name'], 'home');
      expect(proxy['type'], 'wireguard');
      expect(proxy['server'], 'vpn.example.com');
      expect(proxy['port'], 51820);
      expect(proxy['private-key'], _key1);
      expect(proxy['public-key'], _key2);
      expect(proxy['pre-shared-key'], _key3);
      expect(proxy['ip'], '10.8.0.2');
      expect(proxy['ipv6'], 'fd00::2');
      expect(proxy['mtu'], 1380);
      expect(proxy['persistent-keepalive'], 25);
      expect(proxy['udp'], true);
      expect(proxy['allowed-ips'], ['0.0.0.0/0', '::/0']);
      expect(proxy['remote-dns-resolve'], true);
      expect(proxy['dns'], ['1.1.1.1']);
      expect(proxy['amnezia-wg-option'], {
        'jc': 4,
        'jmin': 40,
        'jmax': 70,
        's1': 0,
        'h1': '1',
      });
      final group = (doc['proxy-groups'] as YamlList).single as YamlMap;
      expect(group['name'], 'Proxy');
      expect(group['proxies'], ['home']);
      expect(doc['rules'], ['MATCH,Proxy']);
    });

    test('puts several peers under peers', () {
      const text =
          '''
[Interface]
PrivateKey = $_key1
Address = 10.0.0.2/24

[Peer]
PublicKey = $_key2
Endpoint = [2001:db8::1]:51820

[Peer]
PublicKey = $_key3
Endpoint = 198.51.100.7:51821
AllowedIPs = 10.0.0.0/24
''';
      final doc = _load(profileFromWireGuard(text));
      final proxy = (doc['proxies'] as YamlList).single as YamlMap;
      expect(proxy['name'], 'WireGuard 2001:db8::1');
      expect(proxy.containsKey('server'), isFalse);
      final peers = proxy['peers'] as YamlList;
      expect(peers, hasLength(2));
      expect((peers[0] as YamlMap)['server'], '2001:db8::1');
      expect((peers[0] as YamlMap)['allowed-ips'], ['0.0.0.0/0', '::/0']);
      expect((peers[1] as YamlMap)['port'], 51821);
      expect((peers[1] as YamlMap)['allowed-ips'], ['10.0.0.0/24']);
    });

    test('rejects broken configs with a readable message', () {
      expect(
        () => WireGuardConfig.parse('[Interface]\nPrivateKey = nope\n'),
        throwsA(isA<MessageException>()),
      );
      expect(
        () => WireGuardConfig.parse(
          '[Interface]\nPrivateKey = $_key1\nAddress = 10.0.0.2/32\n',
        ),
        throwsA(
          isA<MessageException>().having(
            (e) => e.message,
            'message',
            contains('[Peer]'),
          ),
        ),
      );
    });
  });

  group('OpenVPN', () {
    test('reads remotes, blocks and directives', () {
      final file = OvpnFile.parse(_ovpn);
      expect(file.needsPassword, isTrue);
      expect(file.remotes.map((r) => '${r.host}:${r.port}/${r.proto}'), [
        'nl1.example.net:1194/udp',
        'nl2.example.net:443/tcp',
      ]);
    });

    test('builds one proxy per remote with the credentials', () {
      final doc = _load(
        profileFromOvpn(
          OvpnFile.parse(_ovpn),
          name: 'nl',
          credentials: const OvpnCredentials('user', 'p@ss: "x"'),
        ),
      );
      final proxies = doc['proxies'] as YamlList;
      expect(proxies, hasLength(2));
      final first = proxies[0] as YamlMap;
      expect(first['name'], 'nl nl1.example.net');
      expect(first['type'], 'openvpn');
      expect(first['proto'], 'udp');
      expect(first['ca'], '$_ca\n');
      expect(first['tls-crypt'], contains('Static key'));
      expect(first['cipher'], 'AES-256-GCM');
      expect(first['data-ciphers'], ['AES-256-GCM', 'AES-128-GCM']);
      expect(first['auth'], 'SHA512');
      expect(first['ping'], 10);
      expect(first['ping-restart'], 60);
      expect(first['username'], 'user');
      expect(first['password'], 'p@ss: "x"');
      expect((proxies[1] as YamlMap)['proto'], 'tcp');
      final groups = doc['proxy-groups'] as YamlList;
      expect((groups[0] as YamlMap)['proxies'], [
        'Auto',
        'nl nl1.example.net',
        'nl nl2.example.net',
      ]);
      expect((groups[1] as YamlMap)['type'], 'url-test');
    });

    test('uses inline <auth-user-pass> credentials', () {
      final file = OvpnFile.parse(
        '$_ovpn<auth-user-pass>\nalice\nsecret\n</auth-user-pass>\n',
      );
      expect(file.needsPassword, isFalse);
      final doc = _load(profileFromOvpn(file));
      final proxy = (doc['proxies'] as YamlList).first as YamlMap;
      expect(proxy['username'], 'alice');
      expect(proxy['password'], 'secret');
    });

    test('requires an inline <ca>', () {
      expect(
        () =>
            profileFromOvpn(OvpnFile.parse('client\nremote a.example 1194\n')),
        throwsA(isA<MessageException>()),
      );
    });
  });

  group('convertVpnConfig', () {
    test('passes other content through untouched', () async {
      final bytes = _bytes('proxies: []\n');
      expect(await convertVpnConfig(bytes), same(bytes));
    });

    test('asks for OpenVPN credentials and names the proxy by file', () async {
      String? askedFor;
      final out = await convertVpnConfig(
        _bytes(
          'client\nremote only.example.net 1194\nauth-user-pass\n'
          '<ca>\n$_ca\n</ca>\n',
        ),
        fileName: 'Only Server.ovpn',
        askCredentials: (server) async {
          askedFor = server;
          return const OvpnCredentials('bob', 'pw');
        },
      );
      expect(askedFor, 'only.example.net');
      final doc = _load(utf8.decode(out));
      final proxy = (doc['proxies'] as YamlList).single as YamlMap;
      expect(proxy['name'], 'Only Server');
      expect(proxy['username'], 'bob');
    });

    test('cancelling the login cancels the import', () async {
      await expectLater(
        convertVpnConfig(_bytes(_ovpn), askCredentials: (_) async => null),
        throwsA(isA<ImportCancelledException>()),
      );
    });

    test('a subscription update reuses the saved login', () async {
      final saved = profileFromOvpn(
        OvpnFile.parse(_ovpn),
        credentials: const OvpnCredentials('carol', "it's secret"),
      );
      final out = await convertVpnConfig(_bytes(_ovpn), previousProfile: saved);
      final proxy =
          (_load(utf8.decode(out))['proxies'] as YamlList).first as YamlMap;
      expect(proxy['username'], 'carol');
      expect(proxy['password'], "it's secret");
    });

    test('without a way to ask, a missing login is an error', () async {
      await expectLater(
        convertVpnConfig(_bytes(_ovpn)),
        throwsA(isA<MessageException>()),
      );
    });
  });

  group('share links', () {
    test('are recognised by scheme', () {
      expect(looksLikeShareLinks('vless://id@1.2.3.4:443#A'), isTrue);
      expect(looksLikeShareLinks('\nss://abc@h:1#B\ntrojan://p@h:443'), isTrue);
      expect(looksLikeShareLinks('https://example.com/sub'), isFalse);
      expect(looksLikeShareLinks('proxies: []'), isFalse);
    });

    test('name the profile after the first link', () {
      expect(
        shareLinksLabel(
          'vless://id@1.2.3.4:443?x=1#Tokyo%20%F0%9F%87%AF%F0%9F%87%B5',
        ),
        'Tokyo 🇯🇵',
      );
      expect(shareLinksLabel('ss://abc@h:1'), 'SS');
    });
  });

  group('OpenVPN engine support', () {
    OvpnFile parse(String extra) => OvpnFile.parse(
      'client\ndev tun\nremote a.example 1194\n$extra\n<ca>\n$_ca\n</ca>\n'
      '<cert>\nC\n</cert>\n<key>\nK\n</key>\n',
    );
    YamlMap proxyOf(String extra) =>
        (_load(profileFromOvpn(parse(extra)))['proxies'] as YamlList).first
            as YamlMap;

    test('defaults auth to SHA1 and offers OpenVPN 2.6 ciphers', () {
      final proxy = proxyOf('');
      expect(proxy['auth'], 'SHA1');
      expect(proxy['data-ciphers'], [
        'AES-256-GCM',
        'AES-128-GCM',
        'CHACHA20-POLY1305',
      ]);
      expect(proxy.containsKey('cipher'), isFalse);
    });

    test('keeps a supported cipher and replaces BF-CBC', () {
      expect(
        proxyOf('cipher AES-256-CBC')['data-ciphers'],
        contains('AES-256-CBC'),
      );
      expect(proxyOf('cipher BF-CBC')['cipher'], 'AES-256-GCM');
    });

    test('passes compress and comp-lzo through', () {
      expect(proxyOf('compress')['compress'], 'stub');
      expect(proxyOf('compress lz4-v2')['compress'], 'lz4-v2');
      expect(proxyOf('comp-lzo no')['comp-lzo'], 'no');
    });

    test('refuses what the engine cannot run', () {
      for (final extra in [
        'dev tap',
        'cipher BF-CBC\ndata-ciphers BF-CBC',
        'compress snappy',
        'pkcs12 cert.p12',
      ]) {
        expect(
          () => profileFromOvpn(parse(extra)),
          throwsA(isA<MessageException>()),
          reason: extra,
        );
      }
    });
  });
}
