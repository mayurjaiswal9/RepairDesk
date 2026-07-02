import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/auth/session_controller.dart';
import '../../../core/error/app_exception.dart';
import '../../../shared/theme/app_theme.dart';
import '../data/auth_repository.dart';

class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key});

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  final _formKey = GlobalKey<FormState>();
  final _userCtrl = TextEditingController();
  final _passCtrl = TextEditingController();

  bool _obscure = true;
  bool _loading = false;
  String? _errorMessage;

  @override
  void dispose() {
    _userCtrl.dispose();
    _passCtrl.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;

    setState(() {
      _loading = true;
      _errorMessage = null;
    });

    try {
      final auth = await ref
          .read(authRepositoryProvider)
          .login(_userCtrl.text.trim(), _passCtrl.text);

      await ref.read(sessionControllerProvider.notifier).signIn(
            token: auth.token,
            userId: auth.userId,
            role: auth.role,
          );
      // Router redirect handles navigation after state change.
    } on AppException catch (e) {
      setState(() => _errorMessage = e.message);
    } catch (e) {
      setState(() => _errorMessage = 'Error: $e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;

    return Scaffold(
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.symmetric(
            horizontal: AppTheme.spaceL,
            vertical: AppTheme.spaceXL,
          ),
          child: Form(
            key: _formKey,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const SizedBox(height: AppTheme.spaceXL),
                _buildHeader(textTheme),
                const SizedBox(height: AppTheme.spaceXL),
                if (_errorMessage != null) _buildErrorBanner(),
                const SizedBox(height: AppTheme.spaceM),
                _buildUsernameField(),
                const SizedBox(height: AppTheme.spaceM),
                _buildPasswordField(),
                const SizedBox(height: AppTheme.spaceXL),
                _buildSignInButton(),
                const SizedBox(height: AppTheme.spaceL),
                _buildRegisterLink(context),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildHeader(TextTheme textTheme) {
    return Column(
      children: [
        Container(
          width: 72,
          height: 72,
          decoration: BoxDecoration(
            color: AppTheme.primary,
            borderRadius: BorderRadius.circular(AppTheme.radiusL),
          ),
          child: const Icon(Icons.build_circle, color: Colors.white, size: 40),
        ),
        const SizedBox(height: AppTheme.spaceM),
        Text('RepairDesk', style: textTheme.titleLarge?.copyWith(fontSize: 26)),
        const SizedBox(height: AppTheme.spaceXS),
        Text(
          'Sign in to continue',
          style: textTheme.bodyMedium?.copyWith(color: AppTheme.textSecondary),
        ),
      ],
    );
  }

  Widget _buildErrorBanner() {
    return Container(
      padding: const EdgeInsets.all(AppTheme.spaceM),
      decoration: BoxDecoration(
        color: AppTheme.error.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(AppTheme.radiusM),
        border: Border.all(color: AppTheme.error.withValues(alpha: 0.4)),
      ),
      child: Row(
        children: [
          const Icon(Icons.error_outline, color: AppTheme.error, size: 18),
          const SizedBox(width: AppTheme.spaceS),
          Expanded(
            child: Text(
              _errorMessage!,
              style: const TextStyle(color: AppTheme.error, fontSize: 13),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildUsernameField() {
    return TextFormField(
      controller: _userCtrl,
      textInputAction: TextInputAction.next,
      keyboardType: TextInputType.emailAddress,
      decoration: const InputDecoration(
        labelText: 'Username',
        prefixIcon: Icon(Icons.person_outline),
      ),
      validator: (v) {
        if (v == null || v.trim().isEmpty) return 'Username is required';
        return null;
      },
    );
  }

  Widget _buildPasswordField() {
    return TextFormField(
      controller: _passCtrl,
      obscureText: _obscure,
      textInputAction: TextInputAction.done,
      onFieldSubmitted: (_) => _submit(),
      decoration: InputDecoration(
        labelText: 'Password',
        prefixIcon: const Icon(Icons.lock_outline),
        suffixIcon: IconButton(
          icon: Icon(_obscure ? Icons.visibility_off : Icons.visibility),
          onPressed: () => setState(() => _obscure = !_obscure),
        ),
      ),
      validator: (v) {
        if (v == null || v.isEmpty) return 'Password is required';
        return null;
      },
    );
  }

  Widget _buildSignInButton() {
    return FilledButton(
      onPressed: _loading ? null : _submit,
      child: _loading
          ? const SizedBox(
              height: 20,
              width: 20,
              child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
            )
          : const Text('Sign In'),
    );
  }

  Widget _buildRegisterLink(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        const Text("Don't have an account? "),
        TextButton(
          onPressed: () => context.push('/register'),
          child: const Text('Register'),
        ),
      ],
    );
  }
}
