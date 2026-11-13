# UltraTel — arte do ícone Android

Coloque o arquivo **ultratel-launcher.png** nesta pasta, mantendo esse nome exato.

A imagem deve ser quadrada, em PNG, preferencialmente com 512×512 pixels,
com o símbolo principal centralizado e sem texto pequeno.

O GitHub Actions executará `scripts/install_ultratel_icon.py` para adaptar
a imagem aos tamanhos de ícone Android (`mdpi`, `hdpi`, `xhdpi`,
`xxhdpi` e `xxxhdpi`) e à camada de ícone adaptativo.

Para enviar pelo celular, abra esta pasta na branch
`feature/ultratel-firebase-20261113`, escolha **Add file → Upload files**
e confirme o envio usando a opção de commit na própria branch.

**Não envie seus arquivos de assinatura (.keystore), base64 ou senhas.**
