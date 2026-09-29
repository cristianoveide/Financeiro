# Gestor Financeiro

O APK Android guarda os dados no SQLite privado do telefone e sincroniza automaticamente um backup no Google Drive. Toque no indicador do topo para entrar na conta Google e ativar a cópia automática. O seletor Android pede que escolha uma pasta do Drive; o app cria e atualiza nela o arquivo `gestor-financeiro-backup.json` sem controles manuais de importação ou exportação.

O app confere a versão do Drive ao abrir, envia cada alteração quando há conexão e preserva as duas cópias quando detecta alterações simultâneas incompatíveis. A sincronização aguarda conexão quando o telefone está offline.

## Google Drive

A primeira conexão abre o provedor de documentos do Google Drive para autenticar a conta, se necessário, e autorizar o acesso à pasta escolhida. Depois disso, o app localiza ou cria o arquivo de backup automaticamente nessa pasta. Em outro aparelho, conecte a mesma conta e escolha a pasta que contém o backup para restaurar/sincronizar os dados.

## Gerar o APK

No Windows com Android Studio instalado, execute `outputs/gestor-financeiro-android/Build-Apk.cmd`. O APK de depuração sai em `outputs/gestor-financeiro-android/android/app/build/outputs/apk/debug/app-debug.apk`. O workflow `Android APK` também compila o app e publica o artefato.

Os arquivos de servidor Node, configuração do Render e código Airtable foram preservados, mas o APK local não os utiliza. Os dados desses serviços permanecem separados do banco do telefone.
