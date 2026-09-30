import 'dart:io';

import 'package:path/path.dart' as p;
import 'package:setup_hooks/src/build_cache.dart';
import 'package:setup_hooks/src/error.dart';
import 'package:setup_hooks/src/go_builder.dart';
import 'package:setup_hooks/src/options.dart';
import 'package:test/test.dart';

void main() {
  late Directory root;
  late File source;
  late GoBuilder builder;

  void git(List<String> args) {
    final result = Process.runSync(
      'git',
      args,
      workingDirectory: source.parent.path,
    );
    expect(result.exitCode, 0, reason: '${result.stderr}');
  }

  setUp(() {
    root = Directory.systemTemp.createTempSync('engine_patches_');
    final engine = Directory(p.join(root.path, 'core', 'Clash.Meta'))
      ..createSync(recursive: true);
    source = File(p.join(engine.path, 'a.go'))..writeAsStringSync('one\n');
    git(['init', '-q']);
    git(['add', 'a.go']);
    source.writeAsStringSync('two\n');
    final diff =
        Process.runSync('git', ['diff'], workingDirectory: engine.path).stdout
            as String;
    source.writeAsStringSync('one\n');
    Directory(p.join(root.path, 'core', 'patches')).createSync();
    File(
      p.join(root.path, 'core', 'patches', '0001-x.patch'),
    ).writeAsStringSync(diff);
    builder = GoBuilder(
      rootDir: root.path,
      config: BuildConfig.load(rootDir: root.path),
      cache: BuildCache(rootDir: root.path),
      notice: BuildNotice(),
    );
  });

  tearDown(() => root.deleteSync(recursive: true));

  test('applies the patches once', () {
    builder.applyEnginePatches();
    expect(source.readAsStringSync(), 'two\n');
    builder.applyEnginePatches();
    expect(source.readAsStringSync(), 'two\n');
  });

  test('reports a patch that no longer applies', () {
    source.writeAsStringSync('three\n');
    expect(builder.applyEnginePatches, throwsA(isA<BuildException>()));
  });
}
