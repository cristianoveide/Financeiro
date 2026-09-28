# Gestor Financeiro para Android

Aplicativo web instalável (PWA), pensado para Android. Ao ser instalado pelo Chrome, abre em tela cheia e tem ícone próprio. Inclui entrada por voz e texto, tema claro/escuro, consulta de lançamentos, categorias, contas e cartões, além de confirmação antes de gravação, correção ou exclusão. O APK Android solicita biometria ou a credencial de bloqueio do aparelho antes de abrir.

## Instalação no Android

O Chrome só oferece a instalação do app em um endereço seguro HTTPS. Publique esta pasta em um serviço Node.js com HTTPS habilitado, configure as variáveis indicadas abaixo e abra o endereço no Chrome do Android. No menu do Chrome, escolha **Instalar app** ou **Adicionar à tela inicial**. Autorize o microfone para comandos falados.

Para testar no computador, use `http://localhost:4173`.

## Gerar o APK Android

O projeto Android está na pasta `android`. No Windows, dê dois cliques em `Build-Apk.cmd`. O script instala a plataforma Android 36 quando necessário, pede a aceitação das licenças do SDK, baixa o Gradle 9.6 e gera um APK de depuração assinado para instalação direta. O build usa Android Gradle Plugin 9.4, compatível com o Java 25 incluído no Android Studio atual. O arquivo será salvo em `android/app/build/outputs/apk/debug/app-debug.apk`; em caso de falha, o diagnóstico completo fica em `android-build.log`. O workflow `Android APK` também compila e publica o APK como artefato do GitHub Actions.

No primeiro uso do APK, informe o endereço HTTPS do servidor publicado. O ícone de engrenagem no topo abre as configurações do servidor. O reconhecimento de voz usa o serviço de fala do Android e pede permissão de microfone quando necessário. O token do Airtable continua somente no servidor.

## Configuração do Airtable

1. Copie `.env.example` para `.env`.
2. Adicione um token pessoal Airtable com os escopos `data.records:read` e `data.records:write`, autorizado para a base `appj4RydYWjdUuD5o`.
3. Execute com Node.js 20 ou superior usando `node server.mjs`.

Em hospedagem Node, configure `AIRTABLE_BASE_ID`, `AIRTABLE_TOKEN`, `PORT` e `NODE_ENV=production`. O servidor então escuta em `0.0.0.0` para o proxy da hospedagem. Termine HTTPS na própria plataforma/proxy. Não publique o token no código do navegador nem exponha a porta diretamente à internet.

## Publicar com HTTPS no Render

Há um `render.yaml` na raiz do projeto `gestor-financeiro`. Envie essa pasta para um repositório GitHub e, no Render, crie um Blueprint conectado a esse repositório. A configuração cria o serviço Node, usa `outputs/gestor-financeiro-android` como raiz e solicita `AIRTABLE_TOKEN` como segredo. Informe um token Airtable com os escopos `data.records:read` e `data.records:write`, autorizado para a base indicada.

Depois que o deploy terminar, copie o endereço HTTPS mostrado no serviço, abra o APK e toque em **Servidor** para colar o endereço raiz (sem `/api`). A rota `/api/status` deve responder `{"connected":true}` quando o token estiver configurado. Render fornece um subdomínio público e termina HTTPS para o serviço.

Contas e cartões começam vazios, sem inventar dados bancários. O usuário pode cadastrá-los por comando, como “Cadastrar conta Nubank corrente” e “Cadastrar cartão Nubank crédito”. Para categorias novas, o app mantém o valor personalizado em campo próprio e deixa “Outros” na seleção padrão do Airtable.

Parcelamentos guardam o valor total da compra, a quantidade de parcelas e a parcela atual; não geram automaticamente um registro futuro por mês. O reconhecimento de voz usa a fala do Chrome para transcrever em português e depende de conexão segura e permissão do microfone.
