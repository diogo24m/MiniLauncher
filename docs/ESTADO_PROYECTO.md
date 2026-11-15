# Estado do MiniLauncher

**MiniLauncher 0.1.0** é a primeira versão deste fork do **StradaOS 2.20.1**. O pacote é `com.mini.launcher`, com código de versão `1`; a edição de teste usa `com.mini.launcher.debug`.

As alterações do fork incluem CAN/SPD, telemetria e manutenção do BMW MINI, velocímetros com prioridade SPD e GPS de reserva, seletor de música Bluetooth/Reprodutor, controlos ADB, identidade MINI e português de Portugal.

Os builds debug/release compilam e os 35 testes instrumentados passaram num emulador Android 11, incluindo os lembretes de manutenção SPD e a telemetria com os quatro tamanhos de letra nos três velocímetros. As 14 evidências do carro mantêm os bytes e os hashes originais. A ligação e os comandos específicos da central, incluindo a escala da velocidade SPD em andamento, precisam de validação no equipamento.

O lint executa com os diagnósticos de compatibilidade e permissões documentados em [MINILAUNCHER.md](MINILAUNCHER.md). Nesta máquina, o lint do AGP 8.7.3 não reconhece a plataforma adicional `android-37.0`; foi executado com uma vista local do SDK limitada à plataforma 35, sem alterar o SDK global.

O histórico de versões do MiniLauncher começa em [0.1.0](../changelogs/0.1.0.md). O StradaOS 2.20.1 é a base de origem; as alterações relativamente a essa base pertencem à primeira versão do fork.

- [Funcionalidades e arquitetura](MINILAUNCHER.md).
- [Dados do carro e evidências](carro/README.md).
- [Como testar](PRUEBAS.md).
- [Compilar e publicar versões](../CONTRIBUTING.md).
