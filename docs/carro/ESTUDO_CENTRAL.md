# Estudo da central BMW MINI — SPD / CAN

Este documento conserva as conclusões do estudo da central e do carro: identificação do equipamento, contrato do serviço SPD, campos descodificados, tramas série, limitações e verificações pendentes. Foi recuperado das notas e análises das capturas de 8–11 de novembro de 2026 e dos registos anteriores. A implementação atual do MiniLauncher está descrita no [guia do carro](README.md).

**Confirmado** significa comparado com um ecrã do fabricante, com o carro ou com uma observação identificada do dono. **Observado** é um valor encontrado numa captura; **hipótese** é uma interpretação ainda por validar. As conclusões mais recentes prevalecem sobre as dúvidas iniciais: por exemplo, o conta-quilómetros foi confirmado pelo dono em 11 de novembro. A recuperação documental não representa uma nova sessão de medição.

Em 13 de novembro de 2026, o dono confirmou a escala de `SETTING_CURRENT_VOLTAGE`: `123` corresponde a **12,3 V**. Esta confirmação resolve a hipótese de décimas de volt descrita nas capturas anteriores.

## Fontes e rastreabilidade

| Referência | Fonte | Contexto |
| --- | --- | --- |
| DIAG | [Exportação de 8 de novembro](evidencias/openlauncher-can-diagnostics.txt) | 25 039 bytes; inclui dez observações de tramas históricas |
| DIAG2 | [Exportação de 9 de novembro](evidencias/openlauncher-can-diagnostics-2026-11-09.txt) | 288 361 bytes, 5 478 linhas; cabeçalho 23:32:00; 600 IDs consultados como inteiros |
| DIAG3 | [Exportação de 10 de novembro](evidencias/openlauncher-can-diagnostics-2026-11-10.txt) | 1 843 976 bytes; sessão 22:05:25–22:18:41; leituras tipadas e callbacks |
| DIAG4 | [Arranque do motor, 10 de novembro](evidencias/openlauncher-can-diagnostics-2026-11-10-2302.txt) | 471 696 bytes; sessão 23:02:01–23:02:50 |
| PHOTO | [Perfil CAN](evidencias/bmw-mini-can-profile.jpg) | Seleção BMW MINI e ID do perfil |
| TRIP | [Computador de bordo](evidencias/2026-11-10-trip-computer.jpg) | Autonomia, consumo, velocidade média e temperatura exterior |
| OIL / BRAKE / INSPECT | [Óleo](evidencias/2026-11-10-engine-oil.jpg), [travões](evidencias/2026-11-10-brake-fluid.jpg), [inspeção](evidencias/2026-11-10-vehicle-inspection.jpg) | Datas, quilómetros e estados de manutenção |
| TYRE / SYSINFO | [Pneus](evidencias/2026-11-10-tyre-pressure.jpg), [sistema](evidencias/2026-11-10-system-info.jpg) | Estado dos pneus e identificação da central |
| CANDBG | [22:16](evidencias/2026-11-10-can-debug-2216.jpg), [22:17 A](evidencias/2026-11-10-can-debug-2217a.jpg), [22:17 B](evidencias/2026-11-10-can-debug-2217b.jpg) | Fotografias das tramas série |
| NOV / MAI / SET | Logcats de novembro de 2025, maio e setembro de 2026 | Resultados conservados do estudo; ficheiros excluídos deste repositório por conterem dados pessoais |

`DIAG3:n` e `DIAG4:n` indicam linhas a partir de 1, removendo apenas o preenchimento NUL. Os nomes e bytes das exportações foram mantidos. Os hashes dos 14 ficheiros disponíveis constam de [SHA256SUMS](evidencias/SHA256SUMS).

## Central e carro

| Item | Valor | Fonte |
| --- | --- | --- |
| Processador | Qualcomm `msm8953` (`QUALCOMM msm8953 for arm64`, produto `msm8953_64`) | exportações de diagnóstico |
| Software | pilha do fabricante **SPD** (não confundir com processador Spreadtrum) | logcats, exportações |
| Android | diz «Android 14», mas é **API 28 (Android 9)**: `msm8953_64-user 9 PKQ1.181105.001 6316.20250708.062524 release-keys`. Usar sempre verificações por nível de API | exportações |
| Sistema | `6316.20250708.062524.G2982`; app `Project_HS_065_Overseas/…` | foto «system info» |
| MCU | `V0.25.0 G10627` | exportações, foto |
| Perfil CAN | **zhongmayitong → BMW → BMW MINI**, ID `135725059` (`0x08170003`), igual a `SETTING_FACTORY_CAR` e a `170004 Vehicles.CURRENT_MODEL` | foto do perfil, exportações |
| Descodificador do fabricante | `com.spd.carinfo.protocol.afterload.BMW.xcarbus.XCarBusBwmCar` (ecrã `XCarbusMiniActivityView`) | logcats |
| Porta série da caixa CAN | o serviço abre `/dev/ttyHSL2` (no logcat de setembro também `/dev/ttyHSL0`; o Bluetooth usa outra) | logcats |
| Texto da caixa CAN | p. ex. `X H117,H3,5045,OKL64,3480322;A0A0`. O 3.º campo (`50xx`) é um **contador** que sobe ~a cada 10 s, não uma versão de firmware. Os dois últimos campos também mudam com o motor (`3480322;A0A0` motor desligado → `7580332;E8A0` / `75C0332;E8A0` com o motor a trabalhar) | exportações, foto |
| Carro | 2 portas + tampa traseira; depósito de 40 L (`SETTING_FUEL_TANK_CAPACITY=40`, é capacidade, não nível) | dono, definições |
| Chaves de Settings.System úteis | `SETTING_HAS_SPD_CAR_SETTING`, `SETTING_CANBOX_VERSION`, `SETTING_FACTORY_CAR`, `SETTING_MCU_VERSION`, `SETTING_CANBOX_LOG` (registo CAN; `0` normalmente), `SETTING_CANBOX_DEBUG`, `SETTING_CURRENT_VOLTAGE`, `SETTING_FUEL_TANK_CAPACITY`, `SETTING_SPEED_UNIT`, `SETTING_TEMPERATURE_UNIT`, `acc_state` | exportações |

Caminho dos dados: caixa CAN → porta série → `XCarBusBwmCar` → `CarInfoManage` → clientes subscritos do fabricante (p. ex. a DVR subscreve `[ReverseAndAVM, Doors]`; o ar condicionado `[AirCondition, Seats, Vehicles, SteeringWheel, Doors, Mirrors, ReverseAndAVM]`). Não se encontrou nenhum broadcast geral com estes valores: os «broadcast actions» da descoberta são candidatos tirados do código, e nenhum foi recebido.

## Contrato do serviço

- Serviço: `com.spd.carinfo/com.spd.carinfo.service.CarInfoService` (exportado, sem permissão). Aceita o bind e as leituras de uma app normal (confirmado em 9 e 10-nov-2026).
- Descritor: `com.spd.carinfo.ICarInfoAidlInterface`. Callback: `com.spd.carinfo.ICarInfoAidlCallback`.

| Transação | Método do serviço | Uso permitido |
| ---: | --- | --- |
| 1 | `void registerCallback(String, String[], ICarInfoAidlCallback)` | sim (1.º argumento = nome do pacote cliente; 2.º = nomes das categorias) |
| 2 | `void unregisterCallback(String, String[])` | sim |
| 3 | `void enterSource(String)` | **nunca** |
| 4 | `void exitSource(String)` | **nunca** |
| 5–8 | `setInt(int, int, int)`, `setFloat(int, float, int)`, `setString(int, String, int)`, `setBundle(int, Bundle, int)` | **nunca** (alteram definições do carro) |
| 9 | `int getInt(int id, int arg)` | sim |
| 10 | `float getFloat(int id, int arg)` | sim |
| 11 | `String getString(int id, int arg)` | sim |
| 12 | `Bundle getBundle(int id, int arg)` | sim |

Callbacks: `1 onCarInfoIntChanged(int id, int valor, int arg)`, `2 onCarInfoFloatChanged(int, float, int)`, `3 onCarInfoStringChanged(int, String, int)`, `4 onCarInfoBundleChanged(int, Bundle, int)`. O cliente de conveniência do fabricante também tem `CarInfo.create(Context, String[], CarInfo.Callback)`.

**Regra de segurança:** chamar só `getName`, `getInt`, `getFloat`, `getString`, `getBundle`, `registerCallback`, `unregisterCallback` e `asInterface`. Não testar transações desconhecidas: há métodos vizinhos que mudam definições do carro. Os IDs de propriedade (p. ex. `100045`) **não** são códigos de transação Binder. Todas as propriedades descodificadas usam o 2.º argumento `0`; os valores `1..4` só foram experimentados nas propriedades de pneus genéricas `70001/70020/70021`, sem significado conhecido.

**Categorias (21):** o ID da categoria é em geral `id / 10000 × 10000`.

| ID | Categoria | ID | Categoria |
| ---: | --- | ---: | --- |
| 10000 | General | 120000 | DriverAssistance (inclui as `MINI_FUNCTION_*`) |
| 20000 | Keys | 130000 | DrivingAndOperating |
| 30000 | AirCondition | 140000 | ReverseAndAVM |
| 40000 | Seats | 150000 | Wipers |
| 50000 | Doors | 160000 | Battery (o `getName` do fabricante devolve «Wipers» — ambiguidade por resolver) |
| 60000 | Windows | 170000 | Vehicles |
| 70000 | WheelsAndTires | 180000 | CarPhone (**excluída**: dados pessoais) |
| 80000 | Mirrors | 190000 | Other |
| 90000 | SteeringWheel | 200000 | AmbientLight |
| 100000 | Instruments (3 039 constantes) | 600000 | CoffeeMaker (excluída) |
| 110000 | Lighting | | |

Na sessão de 10-nov o registo foi aceite para 18 nomes (`AirCondition, AmbientLight, Doors, DriverAssistance, DrivingAndOperating, General, Instruments, Keys, Lighting, Mirrors, Other, ReverseAndAVM, Seats, SteeringWheel, Vehicles, WheelsAndTires, Windows, Wipers`), chegaram 270 callbacks e a varredura completa percorreu as 12 344 consultas tipadas (12 396 valores guardados; 29 192 leituras no total, com 4 erros).

**Aliases:** o mesmo ID pode ter vários nomes na biblioteca partilhada do fabricante (que serve várias marcas). Exemplos: `100045` = `FIAT_CARINFO_BLIND_SPOT_MONITORING` **e** `REMAIN_FUEL`; `100043` = `FIAT_CARINFO_AUTO_PACK_BRAKE` e `INSTANTANEOUS_SPEED`; `100044` = `FIAT_CARINFO_RAIN_SENSOR` e `MILEAGE`. Um nome «Fiat» não quer dizer que o perfil seja Fiat. Guardar todos os aliases e o perfil ativo.

## Campos identificados e grau de confirmação

Valores de referência de DIAG3, exportado em 10-nov-2026 às 22:18 (motor desligado), salvo indicação. A coluna final distingue comparações com fotografias, relatos do dono e confirmações parciais. Os intervalos indicados são limites adotados na descodificação, não especificações publicadas pelo fabricante.

| ID · nome SPD | Getter | Formato | Regra de descodificação | Valor de referência | Como foi confirmado |
| --- | --- | --- | --- | --- | --- |
| `170004` Vehicles.CURRENT_MODEL | `getInt` | int | perfil ativo; `135725059` = BMW MINI. Contexto para `100045` | `135725059` | foto do perfil, `SETTING_FACTORY_CAR` |
| `120283` DriverAssistance.MINI_FUNCTION_SET_UNIT | `getBundle` | `{mSetEnergyUnit, mSetLanguage, mSetTempUnit}` | `mSetEnergyUnit = 0` → consumo em L/100 km. Contexto para `120287` | `{0, 0, 0}` | ecrã do computador de bordo em L/100 km |
| `100045` Instruments.REMAIN_FUEL | `getInt` | int, **litros** | aceitar **só** com `170004 = 135725059` (noutros perfis é uma definição Fiat de ângulo morto); válido 1–150 | `14` L | 14 de 40 L = 35 %, dentro das 3 de 8 barras do ponteiro (37,5 %); 14 L × 100 / 6,5 ≈ 215 km contra 227 km de autonomia; é o byte 2 da trama `0x18` |
| `100011` Instruments.FUEL_RECHARGE_MILEAGE | `getBundle` | `{unit, value}` | `unit = 0` → km; válido 1–3000. A unidade também diz ao `120287` que as distâncias estão em km | `{unit=0, value=227.0}` | ecrã do computador de bordo «227 km» |
| `120287` DriverAssistance.MINI_FUNCTION_CAR_INFO | `getBundle` | `{mCarArriveMile, mCarAverageSpeed, mCarEnergy}` (sem unidade própria) | autonomia km (`mCarArriveMile`, 0–3000) e velocidade média km/h (`mCarAverageSpeed`, 0–300) só se a unidade de distância (de `100011`/`100013`) for km; consumo L/100 km (`mCarEnergy`, 0–50) só se `mSetEnergyUnit = 0`. **Não foi observado callback deste campo na sessão; manter leitura periódica** | `227` / `38.5` / `6.5` | ecrã do computador de bordo: 227 km, 38,5 km/h, 6,5 L/100 km |
| `100013` Instruments.TOTAL_RECHARGE_MILEAGE | `getBundle` | `{unit, value}` | conta-quilómetros total; `unit = 0` → km; válido 1–2 000 000. É um campo de 24 bits na trama `0x18` (bytes 6–8 = `00 B2 F2`) | `{unit=0, value=45810}` | o dono confirmou com o painel de instrumentos (11-nov-2026) |
| `30023` AirCondition.OUT_TEMPERATURE | `getBundle` | `{unit, value}` | `unit = 0` → °C; válido −60 a 70 | `{unit=0, value=19.5}` | barra de estado da central «19.5℃» em todas as fotos |
| `50001` Doors.OPENED | `getBundle` (e callback) | `{frontLeft, frontRight, rearLeft, rearRight, trunk, hood, sunroof}`, ints 0/1 | 1 = aberta. Exigir `frontLeft` e `frontRight` presentes; numa atualização parcial não transformar campos ausentes em «fechada» | tudo `0` | o dono abriu e fechou a porta direita (callback `frontRight` 1 → 0 às 22:17:41) e abriu a esquerda (`frontLeft` 0 → 1 às 23:02:48). Cada porta só mudou o seu campo; `rearLeft/rearRight` ficam sempre `0` neste carro |
| `120288` DriverAssistance.MINI_FUNCTION_TYRE_PRESS | `getBundle` | `{mTyrePressData=[4 ints], mTyrePressUnit, mTyrePressValid, mTyrePressDataValid, mTyrePressStartState, mTyrePressState=[4], mTyrePressProcess, mTyrePressProcessValid, mTyrePressTempValid, mTyrePressSupportReset}` | **décimas de bar**; `−1` = sem leitura (o ecrã mostra «−0.1»); aceitar só com `mTyrePressValid = 1`, `mTyrePressDataValid = 1`, `mTyrePressUnit = 1` (bar); valores 0–60 (0–6,0 bar) | `[-1, -1, -1, -1]`, unit 1, start state 2, process 254 | ecrã de pressão dos pneus: as quatro rodas «(-0.1)», estado «Started», unidade bar. **Parcial:** a escala só foi vista com −1; a ordem das rodas ainda não é conhecida; o dono diz ~2,1 bar com o motor a trabalhar |
| `120290` DriverAssistance.MINI_FUNCTION_MAINTAIN_SERVICE | `getBundle` (e callback, 48 vezes sem mudar) | arrays paralelos, ver abaixo | ver abaixo | ver abaixo | ecrãs «Engine oil», «Brake fluid», «Vehicle inspection» |

**Manutenções (`120290`):** bundle completo em 10-nov:

```
{mMaintainDateMonth=[4, 4, 4], mMaintainDateValid=[1, 1, 1], mMaintainDateYear=[28, 28, 30],
 mMaintainDistance=[0, 18000, 42000], mMaintainDistanceType=[0, 0, 0], mMaintainDistanceUnit=[0, 1, 1],
 mMaintainDistanceValid=[0, 1, 1], mMaintainIndex=2, mMaintainProjectNumber=[3, 1, 2],
 mMaintainProjectState=[0, 0, 0], mMaintainTotal=3}
```

- Número de itens = `mMaintainTotal` (limitado ao tamanho dos arrays).
- `mMaintainProjectNumber`: `1` = óleo do motor, `2` = inspeção do veículo, `3` = líquido de travões (freios). Outros números: mostrar «Serviço N».
- Data: só se `mMaintainDateValid[i] = 1`; ano = `mMaintainDateYear[i] + 2000` (0–99); mês = `mMaintainDateMonth[i]` (1–12).
- Distância restante: só se `mMaintainDistanceValid[i] = 1`, `mMaintainDistanceUnit[i] = 1` (km) e `mMaintainDistanceType[i] = 0` (o ecrã diz «surplus», restante).
- `mMaintainProjectState[i] = 0` → «OK». Outros estados ainda não vistos.
- Resultado em 10-nov: óleo do motor 04/2028, 18 000 km, OK · inspeção 04/2030, 42 000 km, OK · líquido de travões 04/2028, sem distância, OK.

### Tensão da bateria

A central publica a leitura em `Settings.System/SETTING_CURRENT_VOLTAGE`, em décimas de volt, com a escala confirmada pelo dono em 13 de novembro de 2026. Assim, `123 → 121 → 120` corresponde a `12,3 → 12,1 → 12,0 V`, e `117` a `11,7 V`. O MiniLauncher lê esta chave a cada segundo enquanto está visível; não usa o ID SPD `160013`, que devolveu zero nas sessões. Não houve uma nova sessão de captura nem alteração das evidências originais.

## Pistas ainda por confirmar

Estes candidatos podem agora ser consultados como valores brutos na secção «Leituras SPD em validação» dos diagnósticos do MiniLauncher. A apresentação dos IDs e getters não valida as interpretações abaixo nem substitui as comparações com o carro.

| ID / fonte | Valor visto | Interpretação provável | Como confirmar |
| --- | --- | --- | --- |
| `50001` `trunk` | `0` sempre | mala / tampa traseira | abrir a tampa traseira durante uma exportação |
| `140080` ReverseAndAVM.GEAR | `getInt` = `65546` (`0x1000A`), motor desligado | mudança engrenada | sessão de alguns minutos com o motor ligado, mudando de mudança com a embraiagem carregada, anotando a hora de cada mudança |
| `140086` ReverseAndAVM.ACC | `getInt` = 1; callback 0 → 1 às 23:02:27 logo após o arranque | ignição / acessórios | — |
| `acc_state` (Settings) | `1`; passou a `0` durante o arranque (23:02:22, com `SPD_ACC_OFF_TIME`) | estado ACC | — |
| `160013` Battery.VOLTAGE | `getInt` = 0 | — | outro getter / com motor ligado |
| `140062` ReverseAndAVM.CURRENT_SPEED | callbacks float (`6.516`, `4.572`, `3.78`, … `0.0`), todos múltiplos de `0.036` | velocidade atual (cm/s convertidos para km/h?); só manobras lentas observadas | comparar com o GPS em andamento |
| `120291` DriverAssistance.MINI_FUNCTION_CHECK_CONTROL | `{mCheckControlProject=[771, 327], mCheckControlTotal=2}` → `{[327], 1}` após o arranque; às 22:11 `{index 0, null, 0}` | mensagens «Check Control» do painel; significado dos IDs desconhecido | fotografar o painel quando houver avisos |
| `120289` DriverAssistance.MINI_FUNCTION_ENGINE_OIL | `{mEngineOilState=1, mEngineOilSupport=0, mEngineOilValid=1, mEngineOilValue=255}` | leitura de nível de óleo aparentemente indisponível na sessão (`Support=0`, `255`); o «OK» do ecrã corresponde à manutenção `120290` | — |
| `120279` DriverAssistance.MINI_FUNCTION_SET_ENABLED | `{mCarInfoEnable=1, mCarSetEnable=1, mCheckControlEnable=0, mEngineOilEnable=1, mLightEnable=0, mMaintainServiceEnable=1, mTyreWarnEnable=1}` | funções do fabricante ativas para este carro (5 ligadas) | — |
| `100232` Instruments.FUEL_LEVEL | `{color1=0, color2=0, level=0}` | não foi identificada uma percentagem válida nesta propriedade nas capturas | — |
| `3000x` AirCondition | tudo 0 exceto a temperatura exterior | não foi identificado estado válido do A/C nas capturas; zeros não provam ausência de suporte | — |
| `100085` Instruments.MEDIA_SOURCE | `getInt` = 7 (9-nov) | fonte de áudio; enumeração desconhecida | — |
| `140030` ReverseAndAVM.FOOT_BRAKE | `getInt` = 1 às 22:12:19 | travão de pé; o bit 4 do byte 2 da trama `0x12` alterna (candidato) | carregar no travão durante uma captura |
| `120080` DriverAssistance.HAND_BRAKE, `140010` ReverseAndAVM.PARKING | ambos `getInt` = 1 (motor desligado) | travão de mão / estacionamento | — |
| `100004` Instruments.TACHOMETER, `100042` ENGINE_TACHOMETER, `120340` DriverAssistance.TACHOMETER | `getInt` = 0 (motor desligado); nunca lidos com o motor ligado | conta-rotações | ler com o motor ligado |
| `100174` Instruments.LIGHT | 97 (byte 1 da trama `0x08` = `0x61`) | luz/iluminação | — |
| `120280` (luzes) | `mLightCBrightness=9` (byte 0 da trama `0x19`) | brilho | — |
| `120281`–`120286` (definições MINI) | `mSetGotHomeLight=40` (byte 4 da `0x46`), `mSetSpeedWarnValue=136` (byte 7) | definições do carro (ler apenas) | — |
| `SETTING_SPEED_UNIT`, `SETTING_TEMPERATURE_UNIT` | `1`, `1` | códigos do seletor de unidades, enumeração desconhecida | — |
| `70001/70020/70021/70019` WheelsAndTires (genéricas) | `getInt` = 0 | este carro usa `120288` em vez delas | — |
| `100003`, `100017–100019`, `100149` (consumos), `100252/100233/100270` (óleo), `100363` TOTAL_MILEAGE | 0 ou não lidos com o getter certo | alternativas genéricas; neste perfil os valores bons estão nas `MINI_FUNCTION_*` | — |

## Tramas da caixa CAN (protocolo série)

São comandos do protocolo série entre a caixa CAN e a central, **não** IDs CAN nativos do carro.

- Enquadramento: `2E cmd len payload… checksum`, com `checksum = ~(cmd + len + Σpayload) & 0xFF`. Exemplos verificados: `2E 90 01 12 5C` (a central pede o comando `0x12`), `2E 98 04 99 01 00 00 C9`.
- `FF` isolado = confirmação (ACK). A central também envia um registo `5A 00 … 00 5A EF` que não usa o enquadramento `2E`.
- Formatos no logcat: `RaiseGeneralHandle: GeneralHandle:ff 2e 41 10 … 58` (tramas completas, às vezes com ACKs ou várias tramas na mesma linha); `XCarBusBwmCar: rxData:41 10 … ,length:18` (`cmd len payload`, sem cabeçalho nem checksum: não inventar checksum); `SerialHandle: rxACK:IOData …` é fila/ACK, **não** são dados do carro. As duas primeiras representam os mesmos pacotes: não contar duas vezes.
- O ficheiro `CAN_TX_RX_V_6316_M_135725059_0.log` (p. ex. em `/storage/usb1-1-1-p1/`) só recebe tráfego novo com o registo CAN ativo no menu de fábrica (`SETTING_CANBOX_LOG`, normalmente `log=0`). As dez observações das exportações de 8 e 9-nov correspondem a tramas já presentes no ficheiro e coincidem com setembro, incluindo o relógio `1A 09 01 17 36 1A` = 2026-09-01 23:54:26. Não são prova de tráfego novo.
- Fonte das posições: fotos do ecrã «CAN bus debug» (Definições → Fábrica) de 10-nov às 22:16–22:17, comparadas com os valores SPD do mesmo minuto. As 20 tramas transcritas passam no checksum.

| Cmd | Bytes | Propriedades SPD ligadas | Posições conhecidas (bytes do payload, a partir de 0) | Exemplo (10-nov) |
| --- | ---: | --- | --- | --- |
| `0x48` | 10 | `120287` computador de bordo | b0–1 autonomia (`00E3` = 227 km); b2–3 consumo ×0,1 (`0041` = 6,5 L/100 km); b4–5 velocidade média ×0,1 (`0181` = 38,5 km/h); big-endian | `00 E3 00 41 01 81 00 00 00 00` |
| `0x18` | 11 | `100045`, `100013`, `100011` (+ ângulo `140057`) | b2 combustível em L (`0E` = 14); b6–8 conta-km (`00B2F2` = 45 810); b9–10 autonomia (`00E3` = 227); b3–5 desconhecidos | `00 00 0E 00 00 81 00 B2 F2 00 E3` |
| `0x4A` | 8 | `120289` óleo | b1 = `mEngineOilValue` (`FF` = 255) | `90 FF 00 00 00 00 00 00` |
| `0x49` | 24 | `120288` pneus | b3 = `mTyrePressProcess` (`FE` = 254); depois dois blocos de `01` seguidos de quatro pares `FE FF` (um por roda; pressões e provavelmente temperaturas), sem leitura | `A1 55 A8 FE 01 FE FF FE FF FE FF FE FF 01 FE FF FE FF FE FF FE FF 00 00` |
| `0x46` | 12 | `120281`–`120286` definições | b4 `mSetGotHomeLight` = 40; b7 `mSetSpeedWarnValue` = 136; o resto parece conjuntos de bits | `E0 B0 00 70 28 BC 00 88 00 00 00 00` |
| `0x08` | 4 | `120279`, `100174` | b0 `7C` tem 5 bits ligados (= 5 funções ativas?); b1 `61` = LIGHT 97 | `7C 61 A2 02` |
| `0x19` | 4 | `120280` luzes | b0 = `mLightCBrightness` 9 | `09 FF 01 00` |
| `0x34` | 6 | relógio | `AA MM DD hh mm ss` (ano + 2000); hora **ao vivo na fotografia de 10-nov**; outros registos podem ser históricos | `1A 0B 0A 16 10 06` = 2026-11-10 22:16:06 |
| `0x30` | 34 | `10004` General.CANBOX_VERSION | texto ASCII da caixa (`X H117,H3,503D,…`), com o contador | — |
| `0x12` | 6 | `140080`, `140086`, `140030` | o bit 4 do b2 alterna (`97` ↔ `87`); candidatos: travão de pé ou outra entrada momentânea | `00 0A 97 00 81 77` / `00 0A 87 00 81 77` |
| `0x24` | 2 | `140057` **ângulo** (ReverseAndAVM) | **NÃO são portas.** `81 00` gera `angle:10.0`; o descodificador RAISE genérico lia o bit alto de `0x81` como «porta dianteira esquerda aberta» | `81 00` |
| `0x26` | 9 | — | sempre `00 FE FE FE FE FE FE FE FE` | |
| `0x14` | 1 | — | sempre `FE` | |
| `0x20` | 8 | — | 15 payloads diferentes em maio (rajada perto das 00:11:12); zeros em 10-nov | `00 00 00 00 00 00 00 00` |
| `0x41` | 16 | `120264` | uma vez por sessão, no arranque (configuração?) | |
| `0x4B` | 10 | `120290` manutenções | vários payloads indexados; poucas vezes por sessão | |

Inventário de pacotes `XCarBusBwmCar rxData` nos três logcats (os mesmos 16 comandos em todos):

| Cmd | Bytes | NOV | MAI | SET | Cmd | Bytes | NOV | MAI | SET |
| --- | ---: | ---: | ---: | ---: | --- | ---: | ---: | ---: | ---: |
| `0x08` | 4 | 22 | 50 | 5 | `0x30` | 34 | 38 | 87 | 9 |
| `0x12` | 6 | 96 | 227 | 20 | `0x34` | 6 | 28 | 65 | 6 |
| `0x14` | 1 | 21 | 50 | 4 | `0x41` | 16 | 1 | 1 | 1 |
| `0x18` | 11 | 21 | 53 | 4 | `0x46` | 12 | 22 | 50 | 4 |
| `0x19` | 4 | 22 | 51 | 5 | `0x48` | 10 | 22 | 50 | 4 |
| `0x20` | 8 | 21 | 65 | 4 | `0x49` | 24 | 22 | 50 | 4 |
| `0x24` | 2 | 21 | 50 | 4 | `0x4A` | 8 | 22 | 50 | 5 |
| `0x26` | 9 | 21 | 50 | 4 | `0x4B` | 10 | 3 | 4 | 3 |

Total: 403 (NOV) + 953 (MAI) + 86 (SET) = 1 442 pacotes; 1 356 com checksum verificado de forma independente (os 86 de setembro não trazem o checksum).

Não se observaram pacotes `0x21` nessas capturas. Em SET:33409–33417, a trama `0x24` com payload `81 00` é seguida, no mesmo processo/thread do fabricante, de `angle:10.0` e da propriedade `140057`, com argumento normalizado `0.018518519`. É esta correspondência que invalida a interpretação genérica como porta aberta.

O combustível e o conta-quilómetros aparecem em campos separados da trama `0x18`, juntamente com a autonomia: a caixa CAN já os envia à central. As leituras pelo serviço SPD foram possíveis sem adb nem `READ_LOGS`, mesmo com o registo CAN desligado. O ficheiro série só recebe tráfego novo quando esse registo está ativo.

## Regras de interpretação para futuras implementações

1. **API 28**, não «Android 14». Usar sempre `Build.VERSION.SDK_INT`.
2. **Só leitura.** Nunca chamar setters nem `enterSource/exitSource`; não testar transações desconhecidas; deixar a porta série com o serviço do fabricante.
3. **`0x24` não são portas** neste perfil (é ângulo). Não copiar mapeamentos de bits de outros carros nem do RAISE genérico; o enquadramento igual não implica significados iguais.
4. **Unidades:** aceitar só os códigos vistos nos ecrãs (distância `0` = km, temperatura `0` = °C, energia `0` = L/100 km, pneus `1` = bar, manutenção `1` = km) e **descartar** qualquer outro código em vez de adivinhar a conversão.
5. **`100045` só é combustível com o perfil BMW MINI** (`170004 = 135725059`).
6. **Atualizações parciais:** uma atualização só substitui os campos que traz. Nunca transformar um campo ausente em «fechada», «desligado» ou zero (p. ex. não deduzir «todas as portas fechadas» de uma só porta conhecida, nem «A/C desligado» de uma temperatura).
7. **Frescura por campo/domínio:** guardar origem e idade de cada valor; marcar valores antigos como desatualizados; usar `SystemClock.elapsedRealtime()` para a frescura (o relógio de parede salta na central) e a hora de parede só para relatórios.
8. **Histórico ≠ ao vivo:** o que já estava no ficheiro CAN só alimenta estatísticas e nunca atualiza os dados do carro; a hora de leitura não é a idade do pacote.
9. **Distinguir confirmação de hipótese:** o conta-km foi confirmado pelo dono em 11-nov-2026 e a escala da tensão em 13-nov-2026; mala, mudança e escala dos pneus com leituras válidas ainda precisam de confirmação. Não apresentar interpretações novas como medições confirmadas.
10. **Privacidade:** excluir CarPhone; ocultar VIN (17 caracteres), telefone, contactos, IMEI, número de série, MAC e chaves sensíveis nas exportações. Cuidado: a ocultação por substring tapava `Doors.AFTER_DRIVING_INTERLOCKING` (contém «VIN» em «DRIVING»).
11. **Zeros não provam nada:** um `getInt` = 0 pode ser zero real, valor por omissão, getter errado ou campo sem suporte. Experimentar `getBundle`/`getFloat` antes de concluir.
12. **Discovery:** as listas de broadcasts/chaves tiradas dos APKs são candidatos, não APIs confirmadas.

## Próximos passos e verificações pendentes

1. Abrir a tampa traseira para confirmar `trunk` (o conta-km `100013` já está confirmado).
2. Com o motor ligado, manter uma sessão de alguns minutos, mudar de mudança com a embraiagem carregada e exportar, anotando a hora de cada mudança (para mapear `140080 GEAR`). Ler também os conta-rotações.
3. Fotografar o ecrã de pressão dos pneus a ~2,1 bar (motor a trabalhar) para confirmar a escala e a ordem das rodas de `mTyrePressData`; uma foto do «CAN bus debug» na mesma altura mostra que bytes da `0x49` têm as pressões.
4. Capturar o computador de bordo antes e depois de pôr a viagem a zero, para ver se `120287` chega por callback ou só por polling.

As sessões do estudo foram obtidas por exportações e fotografias: o dono não conseguia ligar um computador à central. As verificações seguintes devem continuar a poder ser feitas dessa forma. Se houver ligação adb no futuro, estes comandos recolhem informação; não foram executados nesta limpeza:

```powershell
adb shell pm path com.spd.carinfo
adb shell pm path com.spd.aircondition
adb shell dumpsys package com.spd.carinfo > spd-carinfo-package.txt
adb shell getprop ro.build.version.sdk
adb shell getprop ro.build.version.release
adb logcat -v threadtime "SerialHandle:V" "RaiseGeneralHandle:V" "XCarBusBwmCar:V" "BaseCarModel:V" "CarInfoService:V" "CarInfoManage:V" "CarInfo:V" "*:S" > spd-vehicle-session.txt
```

Usar os caminhos devolvidos por `pm path` para o `adb pull` (cada firmware guarda os APK num sítio). O `READ_LOGS` opcional concede-se com `adb shell pm grant <pacote> android.permission.READ_LOGS`.

## Arranque do motor: sequência observada

DIAG4 durou 49 segundos e percorreu apenas 1 200 das 12 344 consultas tipadas previstas. O dono abriu a porta esquerda, ligou o motor e engrenou a primeira. A sessão curta não leu a mudança, os conta-rotações nem o bundle dos pneus; a ausência desses valores não prova que a central não os tenha.

| Hora da central | Evidência | Leitura e limite da interpretação |
| --- | --- | --- |
| 23:02:01–23:02:19 | DIAG4:2940–2946 | `SETTING_CURRENT_VOLTAGE`: `123 → 121 → 120`; escala de 0,1 V era hipótese à data, confirmada pelo dono em 13-nov-2026 |
| 23:02:20 | DIAG4:2947 | Campo do contador da caixa passa de `501D` a `401E`; os campos finais são `7580332;E8A0` |
| 23:02:22 | DIAG4:2948 e evento seguinte | `acc_state = 0` e registo `SPD_ACC_OFF_TIME`, durante o arranque |
| 23:02:24 | Callback de `120289` | Bundle de óleo sem alteração |
| 23:02:26 | DIAG4:2951 | Campo do contador `501E`; campos finais passam a `75C0332;E8A0` |
| 23:02:27 | DIAG4:2187 | `140086 ACC`: callback de `0` para `1` |
| 23:02:30 | DIAG4:2952 | `SETTING_CURRENT_VOLTAGE = 117` |
| 23:02:33 | DIAG4:2184 | `120291 CHECK_CONTROL`: mensagens `[771, 327]` passam a `[327]`; significado dos IDs desconhecido |
| Até 23:02:48 | DIAG4:1709 | `50001 frontLeft` passa a `1`, com os restantes campos a `0` |

A string da caixa contém estado dinâmico, além de identificação: os campos finais eram `3480322;A0A0` com o motor desligado. A passagem de `50xx` a `40xx` durante o arranque reforça que não se deve tratar o campo completo como uma versão de firmware.

O terceiro campo mudou de `5086` para `509E` em NOV, de `5003` para `503C` em MAI e de `50FA` para `50FF` em SET. DIAG registou `5006 → 5017`. Em DIAG3 houve 52 alterações de `5011` a `5045`, aproximadamente a cada dez segundos; SYSINFO mostra `5037`. São observações do contador/estado, não atualizações sucessivas de firmware.

## O que as capturas permitem concluir

### Leituras e subscrições

- Em DIAG2, o serviço aceitou a ligação e as consultas de uma app normal. Foram lidos 600 IDs com `getInt(id, 0)`, três vezes cada: 596 valores visíveis eram zero, dois eram diferentes de zero e dois estavam ocultados. Não houve callbacks nessa sessão.
- As consultas paravam em `100113`. `100011` e `100013` deram zero com `getInt`, mas DIAG3 obteve autonomia e conta-quilómetros com `getBundle`. Isto demonstra a importância do tipo de leitura; não se deve concluir que um campo está ausente a partir de um inteiro zero.
- O registo de callbacks falhou em DIAG2 antes da chamada ao serviço porque a ferramenta não construía o argumento `String[]`. A mensagem «unregister not found» também não provava ausência do método: a assinatura recuperada é `unregisterCallback(String, String[])`, diferente da assinatura de registo. São limites da recolha dessa sessão, não falhas demonstradas da central.
- Em DIAG3, as subscrições foram aceites e chegaram 270 callbacks. Foram guardados 12 396 valores e 171 alterações recentes; a varredura cobriu as 12 344 consultas tipadas. A manutenção chegou 48 vezes por callback sem mudar. Não foi observado callback do computador de bordo nessa sessão.
- As categorias e constantes pertencem a uma biblioteca partilhada por vários carros. DIAG2 continha 4 896 constantes declaradas nas categorias, 2 969 nomes de propriedades após filtragem e um apêndice limitado a 4 000 constantes. Estes totais descrevem conjuntos diferentes; não são quantidades de funções disponíveis no MINI.
- Os 100 nomes de ações, 400 chaves e 120 classes recolhidos em DIAG2 eram candidatos extraídos de código/metadados. O recetor escutava 35 ações e não recebeu broadcasts: isso não prova inexistência de broadcasts em toda a central. O aviso sobre `AirCondition.apk` (69 MB) coexistia com um ficheiro analisado; não prova que todo o código do ar condicionado tenha ficado por analisar.
- A deteção NWD foi negativa nessa captura. O caminho observado de dados do carro é o serviço SPD.

### Pistas genéricas que não devem ser confundidas com os campos validados

| Grupo | IDs encontrados | Limite das primeiras consultas |
| --- | --- | --- |
| Pneus | `70001`, `70019`, `70020`, `70021`; atualização `70017`, aviso `70032` | Inteiros zero não estabelecem ordem das rodas, unidade ou significado do segundo argumento. A correspondência encontrada neste perfil usa `120288` |
| Óleo | `100252 ENGINE_OIL`, `100233 ENGINE_OIL_PRESSURE`, `100270 OIL_LIFE` | Além do limite de 600 IDs. `100058 OIL_TEMPERATURE = 0` não explica o estado «Normal» |
| Consumo | `100003`, `100017`, `100018`, `100019`, `100149`; autonomia candidata `100148` | Os primeiros quatro deram zero como inteiros; os dois últimos não foram consultados. O consumo confirmado veio de `120287` |
| Distância | `100044 MILEAGE`, `100363 TOTAL_MILEAGE` | O primeiro foi lido sob um alias Fiat; o segundo ficou fora da varredura. O conta-quilómetros confirmado é `100013` |
| Bateria | `16 Battery.ID`, `160013 Battery.VOLTAGE` | O ID de categoria `16` não é uma medição da bateria; o significado e a escala deste ID continuam por confirmar. A leitura validada vem de `Settings.System/SETTING_CURRENT_VOLTAGE` |

As quatro pressões de cerca de 2,1 bar com o motor a trabalhar são um relato do dono, ainda sem captura sincronizada de valores válidos. As fotografias disponíveis mostram `-0.1` nas quatro rodas. Não foi estabelecido se a atualização depende de ignição, motor, movimento ou tempo decorrido.

O estado de óleo «Normal» foi relatado pelo dono; as fotografias posteriores mostram «OK» na manutenção. Não deve ser reinterpretado como confirmação de pressão ou nível físico de óleo. Do mesmo modo, zeros nas propriedades do ar condicionado e no bundle de percentagem de combustível delimitam apenas o que foi observado nas sessões.

### Tráfego histórico, duplicação e relógios

DIAG e DIAG2 incluem dez observações de um ficheiro CAN já existente, com cerca de 2 KB. As seis combinações de direção/comando em DIAG2:950–955 são:

| Direção/comando | Observações | Último payload |
| --- | ---: | --- |
| TX `0x90` | 2 | `12` |
| TX `0x98` | 2 | `99 01 00 00` |
| RX `0x12` | 3 | `00 0A 97 40 81 83` |
| RX `0x30` | 1 | `X H117,H3,50FF,OKL64,3480320;A0A0`, seguido de NUL |
| RX `0x08` | 1 | `FC 61 A2 00` |
| RX `0x34` | 1 | `1A 09 01 17 36 1A` |

A hora do pacote `0x34` corresponde a 2026-09-01 23:54:26, igual a SET:49925; a string da caixa corresponde a SET:49505. Os payloads de `0x12` e `0x08` também coincidem. Isto é forte evidência de importação de histórico, embora o ficheiro CAN original e os seus metadados não tenham sido fornecidos para provar a proveniência exata.

Em DIAG2, todas estas observações receberam a hora de leitura 23:30:59 e não mudaram. Em paralelo, as definições do sistema registaram `5034 → 5035 → 5036 → 5037 → 5038` na string da caixa: havia atividade dessas definições, mas isso não torna as tramas importadas atuais.

Os logs NOV/MAI apresentam duas representações dos mesmos pacotes, `GeneralHandle` e `rxData`, com contagens por comando/payload coincidentes. Contar ambas duplica resultados. Um filtro limitado a `RX:` e `TX:` não encontrou nenhum desses pacotes, apesar das 1 442 entradas do fabricante. Mudar permissões sem suportar os formatos não resolveria essa lacuna de leitura.

As contagens não medem a taxa do barramento: as capturas cobrem durações diferentes e há supressão de mensagens repetidas e ajustes do relógio. MAI contém correções de hora; DIAG salta de 00:02:10 para 18:28:47. Em DIAG2 há um evento às 23:32:01, depois do cabeçalho 23:32:00; a exportação não é uma fotografia atómica e não indica fuso horário.

### Erros observados e limites do diagnóstico

- O estudo original não encontrou marcadores `FATAL EXCEPTION`, `ANR in`, `am_anr`, `am_crash` ou `Fatal signal` nos três logcats. Esta observação aplica-se apenas a esses ficheiros.
- SET:22291 refere a falta de `/spdresource/timezone/GeoIP2-City.mmdb`. O serviço arrancou depois e recebeu pacotes; o erro de dados de fuso horário não demonstra falha total do CAN.
- Erros de `LocationTracker` em MAI pertenciam a outro launcher, `com.khaledisim.carlauncher` (PID 2563, MAI:9086–9088). Não devem ser atribuídos ao MiniLauncher nem à caixa CAN.
- Uma falha inicial de ligação a um serviço de notificações em SET foi seguida de nova tentativa e arranque do processo. Não demonstra defeito persistente de áudio ou CAN.
- Muitas mensagens SELinux indicavam `permissive=1`; não bastam para concluir que uma operação foi bloqueada. A abertura da porta série e o tráfego posterior são evidência mais direta do transporte em funcionamento.
- A etiqueta `<redacted>` não prova que foi lido um VIN: podia resultar apenas do nome da propriedade. A ocultação de «VIN» dentro de «DRIVING» também tapou `50079 Doors.AFTER_DRIVING_INTERLOCKING`.
- O perfil selecionado identifica a configuração de software, não prova por si só o modelo/ano físico do carro ou suporte a todas as funções. As escalas e campos observados não devem ser generalizados para outro firmware ou perfil.

## Referências diretas dos campos

Estas posições permitem reencontrar as leituras sem depender dos relatórios removidos:

| Campo | Referência |
| --- | --- |
| Perfil BMW MINI | DIAG2:12, 1290; DIAG3:12441; PHOTO |
| Plataforma/API/MCU | DIAG2:4–6, 13; SYSINFO |
| Contrato e transações SPD | DIAG2:637–658, 897–927; cliente de conveniência em 696 |
| Temperatura exterior | DIAG3:622; TRIP |
| Autonomia / conta-quilómetros | DIAG3:2659, 2667 |
| Combustível e alias Fiat | DIAG2:568, 2701–2702; DIAG3:2797; DIAG4:2039 |
| Unidades / computador de bordo | DIAG3:9899, 9915; TRIP |
| Pneus / nível de óleo / manutenção | DIAG3:9919, 9923, 9927; TYRE / OIL / BRAKE / INSPECT |
| Porta direita | DIAG3:1774, 12731 |
| Porta esquerda | DIAG4:1709 |
| Mensagens Check Control / ACC | DIAG4:2184, 2187 |
| Fonte de áudio e seletores de unidades | DIAG2:608, 1316, 1318 |
| Trama `0x24` associada a ângulo | SET:33409–33417, propriedade `140057`; resultado conservado do logcat não incluído |
| Abertura da porta série | NOV:20537–20545; SET:22674–22695; resultado conservado dos logcats não incluídos |

## Identificação dos logcats não incluídos

As conclusões sobre estes ficheiros foram recuperadas do estudo anterior. Os ficheiros continuam fora do repositório por conterem dados pessoais; não foram recolhidos nem reanalisados nesta limpeza. Os hashes abaixo identificam as fontes originais e não fazem parte do manifesto dos 14 ficheiros presentes.

| Referência | Ficheiro original | Bytes | Linhas |
| --- | --- | ---: | ---: |
| NOV | `Logcat-2025-11-23-19-45-53.txt` | 7 806 478 | 66 357 |
| MAI | `Logcat-2026-05-08-00-13-30.txt` | 15 894 569 | 126 250 |
| SET | `Logcat-2026-09-01-23-54-41.txt` | 5 581 608 | 49 996 |

```text
NOV a12a6915b4db8442997bf25d87307ac48ff6a9f5afdcc31c397c792313b71c82
MAI ce8cebb53438ddad1cbe48d4ddf9458934a3493ef1b11ad6b3eb962cf9491e74
SET 82f7521d3097937f43faf7842a24967aff8b60b2e9ffa848379ddaeb211d73dd
```

O estudo original verificou comprimentos de pacotes, checksums das tramas completas, concordância das representações duplicadas e correspondência entre tramas e propriedades. Não houve ligação direta ao equipamento nem descompilação de APK nessa análise. Os resultados aqui conservados não certificam todos os estados de funcionamento da central.
