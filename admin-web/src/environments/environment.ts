export const environment = {
  production: false,
  apiBaseUrl: 'https://repairdesk-api-dev.politeground-f86bf509.centralindia.azurecontainerapps.io/api/v1',
  /** Browser key for Maps JavaScript API — restricted by HTTP referrer in Google Cloud. */
  googleMapsApiKey: 'AIzaSyCFgUe7mbNbFIf7qi52YRtrGUzdB0H4RBc',
  /** Customer brand — override per deploy / white-label customer. */
  branding: {
    logoUrl: '/branding/login-logo.png?v=3',
    brandName: 'ACE Telecom',
    productName: 'Field Service Management',
    poweredBy: 'Megagon Technologies Pvt Ltd',
  },
};
