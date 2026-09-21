window.onload = function () {
  window.ui = SwaggerUIBundle({
    url: './openapi.yaml',
    dom_id: '#swagger-ui',
    deepLinking: true,
    validatorUrl: null,
    supportedSubmitMethods: ['get', 'post', 'put', 'delete'],
    tryItOutEnabled: true,
    docExpansion: 'list',
    defaultModelsExpandDepth: 1,
    presets: [SwaggerUIBundle.presets.apis],
    layout: 'BaseLayout'
  });
};
