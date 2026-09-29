import 'package:fl_clash/common/common.dart';
import 'package:fl_clash/widgets/dialog.dart';
import 'package:material_ui/material_ui.dart';

/// Asks for the username and password of an OpenVPN server whose .ovpn file
/// has `auth-user-pass` but no inline credentials.
class OvpnLoginDialog extends StatefulWidget {
  final String server;

  const OvpnLoginDialog({super.key, required this.server});

  @override
  State<OvpnLoginDialog> createState() => _OvpnLoginDialogState();
}

class _OvpnLoginDialogState extends State<OvpnLoginDialog> {
  final _formKey = GlobalKey<FormState>();
  final _usernameController = TextEditingController();
  final _passwordController = TextEditingController();
  bool _obscure = true;

  void _submit() {
    if (_formKey.currentState?.validate() == false) return;
    Navigator.of(context).pop<OvpnCredentials>(
      OvpnCredentials(
        _usernameController.text.trim(),
        _passwordController.text,
      ),
    );
  }

  @override
  void dispose() {
    _usernameController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final appLocalizations = context.appLocalizations;
    return CommonDialog(
      title: appLocalizations.openVpnLogin,
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: Text(appLocalizations.cancel),
        ),
        TextButton(onPressed: _submit, child: Text(appLocalizations.submit)),
      ],
      child: Form(
        key: _formKey,
        child: Wrap(
          runSpacing: 16,
          children: [
            Text(
              appLocalizations.openVpnLoginTip(widget.server),
              style: context.textTheme.bodyMedium,
            ),
            TextFormField(
              controller: _usernameController,
              autofocus: true,
              autofillHints: const [AutofillHints.username],
              textInputAction: TextInputAction.next,
              decoration: InputDecoration(labelText: appLocalizations.username),
              validator: (value) => value == null || value.trim().isEmpty
                  ? appLocalizations.emptyTip(appLocalizations.username)
                  : null,
            ),
            TextFormField(
              controller: _passwordController,
              obscureText: _obscure,
              autofillHints: const [AutofillHints.password],
              onFieldSubmitted: (_) => _submit(),
              decoration: InputDecoration(
                labelText: appLocalizations.password,
                suffixIcon: IconButton(
                  tooltip: appLocalizations.password,
                  icon: Icon(
                    _obscure ? Icons.visibility : Icons.visibility_off,
                  ),
                  onPressed: () => setState(() => _obscure = !_obscure),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
