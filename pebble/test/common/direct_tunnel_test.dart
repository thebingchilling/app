import 'dart:convert';
import 'dart:typed_data';

import 'package:fl_clash/common/direct_tunnel.dart';
import 'package:fl_clash/common/exception.dart';
import 'package:flutter_test/flutter_test.dart';

const _wireGuard = '''[Interface]
PrivateKey = yAnz5TF+lXXJte14tji3zlMNq+hd2rYUIgJBgB3fBmk=
Address = 10.9.0.2/24
DNS = 10.9.0.1

[Peer]
PublicKey = xTIBA5rboUvnH4htodjb6e697QjLERt1NAB4mZqp8Dg=
Endpoint = vpn.example.com:51820
AllowedIPs = 0.0.0.0/0, ::/0
''';

const _amneziaWg = '''[Interface]
PrivateKey = yAnz5TF+lXXJte14tji3zlMNq+hd2rYUIgJBgB3fBmk=
Address = 10.9.0.2/24
Jc = 4
Jmin = 40
Jmax = 70
H1 = 1234567

[Peer]
PublicKey = xTIBA5rboUvnH4htodjb6e697QjLERt1NAB4mZqp8Dg=
Endpoint = vpn.example.com:51820
AllowedIPs = 0.0.0.0/0
''';

const _ovpn = '''client
dev tun
proto udp
remote vpn.example.com 1194
auth-user-pass
<ca>
-----BEGIN CERTIFICATE-----
auth-user-pass inside a block is not a directive
-----END CERTIFICATE-----
</ca>
''';

Uint8List _bytes(String text) => Uint8List.fromList(utf8.encode(text));

String _text(Uint8List bytes) => utf8.decode(bytes);

void main() {
  group('detectDirectTunnel', () {
    test('tells WireGuard, AmneziaWG and OpenVPN apart', () {
      expect(detectDirectTunnel(_wireGuard), DirectTunnelType.wireguard);
      expect(detectDirectTunnel(_amneziaWg), DirectTunnelType.amneziawg);
      expect(detectDirectTunnel(_ovpn), DirectTunnelType.openvpn);
    });

    test('leaves Clash profiles and other text alone', () {
      expect(detectDirectTunnel('proxies:\n  - name: a\n'), isNull);
      expect(detectDirectTunnel('https://example.com/sub'), isNull);
      expect(detectDirectTunnel(''), isNull);
      // A profile Pebble already wrote must not be wrapped again.
      final saved = directTunnelProfile(
        const DirectTunnel(type: DirectTunnelType.wireguard, config: _wireGuard),
      );
      expect(detectDirectTunnel(saved), isNull);
    });

    test('recognises an .ovpn by name when it has no client line', () {
      const bare = 'remote 1.2.3.4 1194\nproto tcp\n';
      expect(detectDirectTunnel(bare), isNull);
      expect(
        detectDirectTunnel(bare, fileName: 'server.ovpn'),
        DirectTunnelType.openvpn,
      );
    });
  });

  group('profile', () {
    test('keeps the file word for word and reads it back', () {
      const tunnel = DirectTunnel(
        type: DirectTunnelType.openvpn,
        config: _ovpn,
        username: 'alice',
        password: 'pa ss"word\\',
      );
      final profile = directTunnelProfile(tunnel);
      expect(profile, contains('rules:\n  - MATCH,DIRECT\n'));
      final back = readDirectTunnel(profile)!;
      expect(back.type, DirectTunnelType.openvpn);
      expect(back.config, _ovpn);
      expect(back.username, 'alice');
      expect(back.password, 'pa ss"word\\');
    });

    test('the tunnel is one line, as the Android service reads it', () {
      final profile = directTunnelProfile(
        const DirectTunnel(type: DirectTunnelType.wireguard, config: _wireGuard),
      );
      final line = profile
          .split('\n')
          .singleWhere((line) => line.startsWith('$directTunnelKey:'));
      final json =
          jsonDecode(line.substring(directTunnelKey.length + 1))
              as Map<String, dynamic>;
      expect(json['type'], 'wireguard');
      expect(json['config'], _wireGuard);
    });

    test('other profiles have no tunnel', () {
      expect(readDirectTunnel('proxies: []\n'), isNull);
      expect(readDirectTunnel(null), isNull);
    });
  });

  group('convertDirectTunnel', () {
    test('passes Clash profiles through unchanged', () async {
      final bytes = _bytes('proxies: []\nrules: []\n');
      expect(await convertDirectTunnel(bytes), same(bytes));
    });

    test('wraps a WireGuard file without asking anything', () async {
      final out = await convertDirectTunnel(
        _bytes(_wireGuard),
        askCredentials: (_) async => fail('WireGuard needs no login'),
      );
      final tunnel = readDirectTunnel(_text(out))!;
      expect(tunnel.type, DirectTunnelType.wireguard);
      expect(tunnel.config, _wireGuard);
      expect(tunnel.hasLogin, isFalse);
    });

    test('asks for the OpenVPN login and stores it', () async {
      String? askedFor;
      final out = await convertDirectTunnel(
        _bytes(_ovpn),
        askCredentials: (server) async {
          askedFor = server;
          return const OvpnCredentials('alice', 'secret');
        },
      );
      expect(askedFor, 'vpn.example.com');
      final tunnel = readDirectTunnel(_text(out))!;
      expect(tunnel.username, 'alice');
      expect(tunnel.password, 'secret');
    });

    test('a subscription update keeps the login entered before', () async {
      final previous = directTunnelProfile(
        const DirectTunnel(
          type: DirectTunnelType.openvpn,
          config: _ovpn,
          username: 'alice',
          password: 'secret',
        ),
      );
      final out = await convertDirectTunnel(
        _bytes(_ovpn),
        previousProfile: previous,
        askCredentials: (_) async => fail('login was saved'),
      );
      expect(readDirectTunnel(_text(out))!.username, 'alice');
    });

    test('cancelling the login cancels the import', () async {
      expect(
        () => convertDirectTunnel(
          _bytes(_ovpn),
          askCredentials: (_) async => null,
        ),
        throwsA(isA<ImportCancelledException>()),
      );
    });

    test('an inline <auth-user-pass> needs no prompt', () async {
      const inline =
          'client\ndev tun\nremote a.example 1194\nauth-user-pass\n'
          '<auth-user-pass>\nbob\npw\n</auth-user-pass>\n';
      expect(ovpnNeedsLogin(inline), isFalse);
      expect(ovpnNeedsLogin(_ovpn), isTrue);
      expect(ovpnNeedsLogin('client\ndev tun\nremote a 1\n'), isFalse);
    });

    test('refuses dev tap, which Android cannot carry', () async {
      expect(
        () => convertDirectTunnel(
          _bytes('client\ndev tap\nremote a.example 1194\n'),
        ),
        throwsA(isA<MessageException>()),
      );
    });
  });
}
