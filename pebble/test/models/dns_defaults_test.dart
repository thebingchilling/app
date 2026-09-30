import 'dart:convert';

import 'package:fl_clash/models/models.dart';
import 'package:flutter_test/flutter_test.dart';

/// As the settings store keeps it.
Map<String, Object?> _stored(Dns dns) =>
    jsonDecode(jsonEncode(dns)) as Map<String, Object?>;

void main() {
  test('defaults do not depend on Chinese resolvers', () {
    const dns = Dns();
    final servers = [
      ...dns.defaultNameserver,
      ...dns.nameserver,
      ...dns.proxyServerNameserver,
      ...dns.fallback,
      ...dns.nameserverPolicy.values,
    ];
    expect(dns.nameserver.first, 'system');
    expect(dns.proxyServerNameserver.first, 'system');
    expect(dns.nameserverPolicy, isEmpty);
    expect(
      servers.where(
        (s) =>
            s.contains('doh.pub') ||
            s.contains('alidns') ||
            s.contains('223.5.5.5'),
      ),
      isEmpty,
    );
  });

  test('saved FlClash defaults move to the new defaults', () {
    expect(Dns.safeDnsFromJson(_stored(legacyFlClashDns)), const Dns());
  });

  test('DNS settings the user changed are kept', () {
    final custom = legacyFlClashDns.copyWith(nameserver: ['tls://9.9.9.9']);
    expect(Dns.safeDnsFromJson(_stored(custom)), custom);
  });
}
