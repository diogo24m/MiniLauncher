# MiniLauncher 0.1.0

Primeira versão do MiniLauncher, um fork do [StradaOS 2.20.1](https://github.com/lordnordic-debug/StradaOS), de lordnordic-debug, sob a licença GPL-3.0. A base corresponde ao commit `b4b8eed`.

| Identificação | Valor |
| --- | --- |
| Nome | MiniLauncher |
| Versão | 0.1.0 |
| Código de versão | 1 |
| Pacote | `com.mini.launcher` |
| Pacote de teste | `com.mini.launcher.debug` |

## Alterações do fork relativamente ao StradaOS 2.20.1

- Dados BMW MINI por CAN/SPD: combustível, autonomia, consumo, tensão da bateria, temperatura exterior, portas, pneus, quilometragem e manutenção.
- Telemetria no cartão do carro, diagnóstico ao tocar, dados nas definições e avisos de portas. A quilometragem real alimenta os lembretes de manutenção.
- Serviços de manutenção SPD integrados nas secções originais, com o óleo em **Mudança de óleo**, toggle por serviço e três colunas por baixo da linha divisória: data prevista, distância restante e estado. Incluem indicador do início, avisos por data/distância, adiamento e conclusão local até o carro informar a próxima manutenção.
- Diálogo do indicador de óleo/manutenção com resumo compacto de todos os serviços, incluindo os lembretes manuais, e acesso às ações de cada um.
- Velocímetros com prioridade à velocidade SPD, atualizada por avisos e consulta a cada segundo. Se a leitura faltar, ficar antiga ou o serviço desligar, usam o GPS. A escala SPD é tratada como km/h e ainda precisa de confirmação em andamento.
- Diagnóstico bruto SPD, com IDs e idade da consulta, para os campos que ainda precisam de validação.
- Demonstração estática identificada como dados simulados no build `.debug` sem serviço SPD, sem alimentar o velocímetro nem alterar a quilometragem guardada.
- Seletor Bluetooth/Reprodutor, filtragem e atualização das sessões de música, capas por URI, pedido de acesso a notificações e diagnóstico do player.
- Controlos de ADB USB/Wi-Fi em Sistema, com estado desconhecido explícito e ajuda para conceder permissões ao pacote instalado.
- Nome e pacote próprios, ícone e logótipo MINI, português de Portugal e controlo de texto de 85 a 150%.
- [Documentação do carro](carro/README.md), estudo da central e 14 evidências originais com os respetivos hashes.

Os mapas, os modos digital/analógico/misto do velocímetro, o mostrador sobre o mapa, a organização das definições e os lembretes por distância/dias/semanas/meses fazem parte da base StradaOS 2.20.1. A disposição do cartão foi ajustada para acomodar a telemetria e o texto grande.

## Compilação e validação

```powershell
.\gradlew.bat assembleDebug assembleRelease
.\gradlew.bat connectedDebugAndroidTest
```

Os testes instrumentados usam um emulador Android 11 sem SPD. Exercitam o contrato com um Binder de teste e Bundles reais: unidades, valores ausentes, escalas, cadência de consulta, avisos e manutenção incompleta. Também verificam prioridade SPD/GPS, expiração independente, perda de ligação, conversão para mph, todos os mostradores, texto a 150%, música, categorias de definições e lembretes SPD por data/distância, incluindo adiamento e conclusão.

Os testes no emulador complementam a validação na central real: ligação SPD, escala da velocidade em andamento, comandos de música e permissões ADB. Os campos por confirmar estão identificados em [carro/README.md](carro/README.md).

O lint gera `app/build/reports/lint-results-debug.html`. Mantém diagnósticos de permissões, APIs e IDs de recursos da base, do target SDK 28 conservado para centrais antigas e do pedido de `WRITE_SECURE_SETTINGS`. A configuração atual não bloqueia a compilação por estes diagnósticos.

O APK release local fica sem assinatura quando não estão definidas as variáveis `MINILAUNCHER_KEYSTORE`, `MINILAUNCHER_KEYSTORE_PASSWORD`, `MINILAUNCHER_KEY_ALIAS` e `MINILAUNCHER_KEY_PASSWORD`. O APK debug usa a chave de desenvolvimento. Os links de atualização apontam para `diogo24m/MiniLauncher`; a publicação é feita pelo workflow de release.
