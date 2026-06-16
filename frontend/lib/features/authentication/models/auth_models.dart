class AuthResponse {
  final String token;
  final String userId;
  final String username;
  final String role;
  final bool mustChangePassword;

  const AuthResponse({
    required this.token,
    required this.userId,
    required this.username,
    required this.role,
    required this.mustChangePassword,
  });

  factory AuthResponse.fromJson(Map<String, dynamic> json) {
    return AuthResponse(
      token: json['token'] as String,
      userId: json['userId'] as String,
      username: json['username'] as String,
      role: json['role'] as String,
      mustChangePassword: json['mustChangePassword'] as bool? ?? false,
    );
  }
}
