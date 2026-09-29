# Gestor Financeiro

O APK Android em `outputs/gestor-financeiro-android/android` funciona localmente, sem Render, Airtable ou permissão de internet. Lançamentos, categorias, contas e cartões são guardados numa base SQLite privada do app no telefone. A versão mantém a interface escura azul, biometria, comandos por voz/texto, consultas e confirmação para gravações, correções e exclusões.

## Migrar uma cópia dos dados do Airtable

1. No Airtable, exporte cada tabela necessária como CSV. Isso apenas lê/exporta a base; não altere nem exclua registros.
2. Transfira os arquivos CSV para o telefone. Abra **Seus dados → Importar CSV / JSON**, selecione a tabela correspondente ao arquivo e escolha o CSV. Repita para Lançamentos, Categorias, Contas e Cartões.
3. Revise os dados importados na tela e crie um backup JSON com **Exportar backup**.

A importação exige confirmação, valida as linhas e só acrescenta registros ao aparelho. Importar o mesmo arquivo novamente não duplica os itens. A importação não envia pedidos à rede, não modifica nem elimina dados no Airtable ou no Render. Mantenha uma cópia dos CSVs e do backup JSON em local seguro.

O banco é privado ao app no armazenamento local do Android e o backup automático Android está desativado. Desinstalar o app ou limpar seus dados apaga a base local; exporte um backup antes disso. A biometria protege a abertura do app.

O reconhecimento de voz pede preferência por processamento offline. A disponibilidade depende de o serviço de reconhecimento do aparelho ter o pacote de português instalado; comandos de texto continuam disponíveis sem rede. A fala de confirmação usa o sintetizador de voz instalado no telefone.

## Gerar o APK

No Windows com Android Studio instalado, execute `outputs/gestor-financeiro-android/Build-Apk.cmd`. O APK de depuração sai em `outputs/gestor-financeiro-android/android/app/build/outputs/apk/debug/app-debug.apk`. O workflow `Android APK` também compila o app e publica o artefato.

## Componentes online antigos

Os arquivos de servidor Node, configuração do Render e código de integração Airtable foram preservados no repositório. O APK local não os carrega, não os chama e não inclui permissão Android de internet. Dados existentes nos serviços continuam separados e inalterados.
