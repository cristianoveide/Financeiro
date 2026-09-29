# Gestor Financeiro Offline para Android

O APK abre a interface incluída no próprio app e guarda os dados no SQLite privado do telefone. O backup opcional no Google Drive sincroniza um arquivo JSON selecionado pelo usuário. O app mantém biometria, comandos por texto e voz, lançamentos, categorias, consultas, correções e exclusões com confirmação.

## Backup automático no Google Drive

Na primeira vez, toque no indicador de backup no topo e selecione no seletor do Android um arquivo JSON existente no Google Drive ou crie um novo arquivo. Essa autorização pontual é necessária para o Android conceder acesso persistente ao documento. Depois, alterações no app são enfileiradas para backup e enviadas quando houver conexão; ao abrir, o app confere se a cópia do Drive é mais recente e restaura quando só ela mudou. Se as duas cópias tiverem mudanças diferentes, o app preserva os dados e pede para escolher qual manter. O Drive pode ser desconectado no mesmo indicador; o app continua funcionando offline.

## Copiar seus dados atuais

No Airtable, exporte cada tabela como CSV e transfira os arquivos ao telefone. No app, abra **Seus dados**, escolha **Importar CSV / JSON**, selecione a tabela do arquivo e importe. Repita para cada tabela. A confirmação apresenta o conteúdo a importar; só então o app copia os registros para o SQLite local. A importação acrescenta dados, evita duplicatas ao reimportar o mesmo arquivo e nunca escreve no Airtable ou Render.

Também é possível importar JSON no formato de backup do app ou registros Airtable (`records` com `fields`). **Exportar backup** continua disponível para salvar manualmente um arquivo JSON. Desinstalar o app ou limpar seus dados remove a cópia local; mantenha o backup do Drive conectado ou exporte uma cópia antes disso.

## Voz e segurança

O app solicita processamento de voz offline quando suportado pelo serviço de reconhecimento instalado e pelo pacote de português baixado. Se o aparelho não reconhecer fala offline, use os comandos por texto. A biometria ou credencial de tela é solicitada ao abrir. O banco fica no armazenamento privado do app e a permissão de rede permanece fora do app: o acesso ao Drive é feito pelo provedor de documentos do Android após a autorização do usuário.

## Gerar o APK

Execute `Build-Apk.cmd` no Windows com Android Studio instalado. O APK de depuração é salvo em `android/app/build/outputs/apk/debug/app-debug.apk`. O GitHub Actions também compila e publica o APK como artefato.

Os arquivos `server.mjs`, `render.yaml` e o fluxo do Airtable continuam no repositório como componentes online antigos; o APK local não depende deles. Nenhum dado remoto foi alterado durante a migração do aplicativo.
