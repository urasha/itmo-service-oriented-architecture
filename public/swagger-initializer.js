window.onload = function () {
  window.ui = SwaggerUIBundle({
    urls: [
      {name: 'Flat Collection Service', url: './flats.yaml'},
      {name: 'Agency Service', url: './agency.yaml'}
    ],
    'urls.primaryName': 'Flat Collection Service',
    dom_id: '#swagger-ui',
    deepLinking: true,
    validatorUrl: null,
    supportedSubmitMethods: ['get', 'post', 'put', 'delete'],
    tryItOutEnabled: true,
    docExpansion: 'list',
    defaultModelsExpandDepth: 1,
    presets: [SwaggerUIBundle.presets.apis, SwaggerUIStandalonePreset],
    layout: 'StandaloneLayout'
  });
};
