// Copie para uma Action do tipo Login / Post Login no SEU tenant.
// Deploy a Action e adicione ao fluxo de Login para que ela execute.
exports.onExecutePostLogin = async (event, api) => {
  const namespace = 'https://pf-avaliacoes.example.com';
  const roles = (event.authorization?.roles ?? []).filter(role => ['ADMIN', 'USER'].includes(role));
  api.idToken.setCustomClaim(`${namespace}/roles`, roles);
  api.accessToken.setCustomClaim(`${namespace}/roles`, roles);
  if (event.user.email) {
    api.accessToken.setCustomClaim(`${namespace}/email`, event.user.email);
  }
};
