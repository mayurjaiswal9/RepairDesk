export const environment = {
  production: true,
  apiBaseUrl:
    'https://repairdesk-api-dev.politeground-f86bf509.centralindia.azurecontainerapps.io/api/v1',
  /** Customer brand — override per deploy / white-label customer. */
  branding: {
    logoUrl: '/branding/login-logo.png?v=3',
    brandName: 'ACE Telecom',
    productName: 'Field Service Management',
    poweredBy: 'Megagon Technologies Pvt Ltd',
  },
};
