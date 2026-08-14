export const environment = {
  production: false,
  apiBaseUrl: 'http://localhost:8080/api/v1',
  /** Customer brand — override per deploy / white-label customer. */
  branding: {
    logoUrl: '/branding/login-logo.png?v=3',
    brandName: 'ACE Telecom',
    productName: 'Field Service Management',
    poweredBy: 'Megagon Technologies Pvt Ltd',
  },
};
