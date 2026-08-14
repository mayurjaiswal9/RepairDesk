/// Canonical role strings from the API (`UserRole.name()`).
abstract final class Roles {
  static const admin = 'ADMIN';
  static const staff = 'STAFF';
  static const technician = 'TECHNICIAN';
  static const customer = 'CUSTOMER';

  /// Normalize whatever the API / storage returned so comparisons are reliable.
  static String normalize(String? role) =>
      (role ?? '').trim().toUpperCase().replaceFirst(RegExp(r'^ROLE_'), '');

  static bool isTechnician(String? role) => normalize(role) == technician;

  static bool isAdmin(String? role) => normalize(role) == admin;

  static bool isStaff(String? role) => normalize(role) == staff;

  static bool isStaffOrAdmin(String? role) {
    final r = normalize(role);
    return r == admin || r == staff;
  }

  static bool canAccessCustomers(String? role) => isStaffOrAdmin(role);
}
