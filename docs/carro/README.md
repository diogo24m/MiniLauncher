# Dados do carro no MiniLauncher

O MiniLauncher lê dados BMW MINI em centrais com o serviço SPD `com.spd.carinfo`. A implementação está em [CarInfo.java](../../app/src/main/java/com/mini/launcher/CarInfo.java) e o modelo dos dados em [Vehicle.java](../../app/src/main/java/com/mini/launcher/Vehicle.java).

Os dados aparecem no cartão do veículo e em **Ajustes → Tu vehículo → Diagnóstico del carro**. O cartão mostra combustível, autonomia e consumo numa primeira linha, com tensão da bateria e temperatura exterior numa segunda linha; a foto cede espaço a estes dados. Tocar em qualquer parte desta área abre um diagnóstico com os mesmos campos das definições. Quando disponível, o conta-quilómetros do carro também é usado pelo lembrete de mudança de óleo.

A manutenção é apresentada apenas em **Definições → Manutenção**, com secções no estilo original: título exterior, cabeçalho do lembrete com toggle e linha divisória antes dos dados. O óleo do motor SPD integra a secção **Mudança de óleo**, sem repetir a configuração manual, incluindo na demonstração. A inspeção e o líquido dos travões usam o mesmo estilo. As informações aparecem em três colunas: data prevista, distância restante e estado. A última coluna inclui o estado do carro e do lembrete; as ações partilham a mesma fila por baixo. Os dados em falta aparecem como `—`; só se mostra `OK` quando esse estado vem do carro. Os valores atualizam-se automaticamente. “O meu veículo” e o diagnóstico geral não repetem estes serviços.

## Manutenção e lembretes do carro

Em **Definições → Manutenção**, óleo do motor, inspeção e líquido dos travões enviados pelo serviço `120290` aparecem em secções individuais, cada uma com o seu toggle. Os lembretes estão ativos por omissão; desativar um mantém os dados do carro visíveis por baixo. As ações **Já fiz**, **Ainda não** e **Voltar a avisar** aparecem junto do respetivo serviço quando o lembrete está ativo. Os outros lembretes continuam disponíveis para serviços definidos pelo utilizador.

Estes serviços participam no mesmo indicador do início e nos mesmos avisos dos lembretes manuais. O aviso vence quando a distância restante chega a zero ou começa o mês previsto, o que acontecer primeiro; o SPD indica apenas mês e ano. O indicador destaca serviços próximos a cerca de 1000 km ou 30 dias. Não se mostra uma percentagem de vida para os serviços SPD porque o carro não fornece o intervalo total. Dados de data/distância em falta não geram um vencimento, e o estado desconhecido não é interpretado como manutenção vencida.

Tocar no indicador de óleo/manutenção do início abre um resumo compacto de todos os serviços SPD e dos lembretes manuais, incluindo serviços desativados ou sem prazo disponível. Cada item apresenta data prevista, distância restante e estado em três colunas. Tocar num serviço abre as suas ações; o botão **Definições** dá acesso à configuração completa. O óleo SPD ocupa o lugar do manual, e os dados simulados aparecem identificados e sem ações que alterem os lembretes.

- **Ainda não:** adia até mais 100 km (60 mi) ou sete dias, o que acontecer primeiro. Serviços sem distância, ou sem conta-quilómetros disponível, usam apenas os sete dias.
- **Já fiz:** regista a conclusão apenas no MiniLauncher e suspende esse lembrete até o carro informar uma data posterior ou uma distância restante maior em mais de 100 km. Não altera nem reinicia a manutenção no carro.
- **Desativar/ativar lembrete:** conserva a escolha por serviço entre arranques. **Voltar a avisar** remove a conclusão e o adiamento locais.

O óleo recebido do SPD substitui o aviso manual de óleo enquanto tiver uma data ou distância válida, evitando dois avisos para o mesmo serviço. Sem esse prazo, a mesma secção mostra a configuração manual guardada. Datas, distâncias e estado continuam a vir do carro; ativação, adiamento e conclusão são preferências locais guardadas por código de serviço. As leituras atualizam as linhas e o indicador automaticamente, incluindo quando chegam após abrir o launcher. Os dados simulados são apenas uma pré-visualização e não criam lembretes.

## Velocímetro: SPD com GPS de reserva

Os mostradores digital, analógico, misto, da barra superior e sobre o mapa dão prioridade à velocidade atual SPD (`140062`, `ReverseAndAVM.CURRENT_SPEED`) no perfil BMW MINI. A app trata o valor como km/h e converte-o para mph quando essa é a unidade escolhida. Esta escala é a hipótese usada na integração pedida pelo dono; as capturas registam apenas manobras lentas e ainda falta comparar com o GPS em andamento.

A velocidade é consultada a cada segundo e atualizada também por `onCarInfoFloatChanged`, com argumento `0`. Um aviso recebido nos últimos 3 segundos tem prioridade sobre o getter, para centrais que só atualizam o valor nos avisos. Zero é uma velocidade válida; valores negativos, não finitos ou acima de 400 km/h são rejeitados. O indicador junto ao mostrador apresenta **SPD** quando essa fonte está ativa.

Se o serviço desligar, a leitura estiver indisponível ou tiver mais de 3 segundos, o velocímetro usa a última velocidade GPS com até 6 segundos. Sem velocidade válida em nenhuma fonte mostra `--`. A seleção é reavaliada quando chegam leituras, ao reconstruir o ecrã e no temporizador do launcher (a cada 2 segundos). A idade da velocidade é independente das outras leituras do carro. Os dados de demonstração não alimentam o velocímetro. O GPS continua a fornecer a posição do mapa.

## Leituras SPD em validação

O diagnóstico do cartão e as definições incluem uma secção separada com os campos adicionais identificados nas capturas. São valores brutos, sem atribuir unidades ou interpretar códigos ainda por confirmar. Um zero pode ser uma resposta por omissão ou falta de suporte; não é tratado como confirmação de um estado.

| Campo | ID SPD | Getter |
| --- | --- | --- |
| Avisos Check Control | `120291` | `getBundle`: total e códigos, sem traduzir os códigos em mensagens |
| Ignição / ACC | `140086` | `getInt` |
| Travão de pé | `140030` | `getInt` |
| Travão de mão | `120080` | `getInt` |
| Estacionamento | `140010` | `getInt` |
| Mudança engrenada | `140080` | `getInt`, sem mapear para N/R ou uma mudança |
| Regime do motor | `100004`, `100042`, `120340` | `getInt`, três leituras independentes sem assumir rpm |
| Velocidade CAN | `140062` | `getFloat`, valor bruto no diagnóstico; usado como km/h no velocímetro conforme descrito acima |
| Luzes | `100174` | `getInt` |
| Brilho das luzes | `120280` | `getBundle`: `mLightCBrightness` |
| Nível do óleo | `120289` | `getBundle`: suporte, validade, estado e valor; sem converter em percentagem |

As consultas usam os códigos de transação encontrados no serviço instalado; `getFloat` é opcional e a ausência do método não impede as outras leituras. A recolha adicional reutiliza o ciclo existente, a cada 10 segundos, apenas enquanto a app está visível. O mapa de cada consulta substitui o anterior, incluindo resultados indisponíveis, e conserva a hora monotónica da consulta. Se ficar sem atualizar por mais de 30 segundos, o diagnóstico assinala esse facto. O diagnóstico do cartão mostra uma leitura ao abrir; as definições continuam a atualizar os valores. O build de teste inclui exemplos estáticos assinalados como simulados.

Só se consultam os IDs da tabela; não se faz uma varredura de categorias nem se chamam setters. Os campos de climatização, bancos, vidros e espelhos continuam sem uma interpretação validada para este MINI.

No build de teste (`.debug`), quando não existe serviço SPD, o cartão e o diagnóstico mostram uma demonstração fixa identificada como **Dados simulados**. Esta demonstração não liga ao CAN, não cria leituras periódicas nem altera o conta-quilómetros usado pelo lembrete de óleo. Com serviço SPD, continuam a aparecer os dados reais; o build normal usa sempre o serviço real.

O [estudo da central](ESTUDO_CENTRAL.md) conserva as conclusões da investigação: identificação do equipamento, contrato SPD, mapeamentos, tramas CAN, sequência de arranque e hipóteses por confirmar, com referências às evidências.

## Ligação e leituras

- Liga-se a `com.spd.carinfo/.service.CarInfoService` e confirma o descritor `com.spd.carinfo.ICarInfoAidlInterface`.
- Obtém os códigos Binder das classes do serviço instalado na central. Os IDs de propriedade da tabela abaixo não são códigos de transação Binder.
- Usa `getInt`, `getFloat`, `getBundle`, `registerCallback` e `unregisterCallback`. A integração é de leitura; não chama setters nem muda a fonte de áudio.
- Enquanto o MiniLauncher está visível, lê velocidade, portas, temperatura exterior e tensão da bateria a cada segundo, pneus a cada minuto e os restantes dados a cada 10 segundos. A tensão é lida de `Settings.System/SETTING_CURRENT_VOLTAGE`, mesmo se a ligação ao serviço CAN estiver temporariamente indisponível.
- Subscreve os avisos de `Doors` e `ReverseAndAVM`, usando apenas os campos de portas e velocidade; a leitura periódica continua a funcionar como reserva. Ao sair do ecrã, interrompe as leituras e a subscrição.

## Propriedades utilizadas

| ID SPD | Dados | Interpretação na app |
| --- | --- | --- |
| `170004` | Perfil CAN | `135725059` identifica BMW MINI |
| `100045` | Combustível restante | Litros; aceite apenas com o perfil BMW MINI |
| `100011` | Autonomia | `unit = 0`: quilómetros |
| `100013` | Conta-quilómetros | `unit = 0`: quilómetros |
| `120283` | Unidades do carro | `mSetEnergyUnit = 0`: L/100 km |
| `120287` | Computador de bordo | Autonomia, consumo e velocidade média, com as unidades validadas |
| `30023` | Temperatura exterior | `unit = 0`: °C |
| `50001` | Portas | Campos `frontLeft`, `frontRight`, `rearLeft`, `rearRight`, `trunk` e `hood` |
| `120288` | Pneus | `mTyrePressUnit = 1`: décimas de bar; valores negativos indicam ausência de leitura |
| `120290` | Manutenção | Óleo, inspeção e líquido de travões; datas e quilómetros restantes quando válidos |

A tensão da bateria vem de `Settings.System/SETTING_CURRENT_VOLTAGE`, em décimas de volt: `123` corresponde a **12,3 V** e `117` a **11,7 V**. A escala foi confirmada pelo dono em 13 de novembro de 2026. A app aceita valores positivos até `320` (32,0 V); se a chave estiver ausente, inacessível ou tiver um valor inválido, mostra `—`, sem conservar uma leitura anterior. No build de teste sem SPD, o valor simulado é **12,6 V**.

Não deduzir o significado de um campo pelo nome de uma constante de outro perfil CAN. A app aceita apenas as unidades e os intervalos definidos em `CarInfo.java`.

## Verificações no carro

As evidências registam combustível, autonomia, consumo, velocidade média, temperatura exterior, manutenção e alterações das portas dianteiras. O conta-quilómetros foi confirmado pelo dono em 11 de novembro de 2026.

Falta confirmar a mala aberta e a ordem/escala das pressões com os pneus a apresentar leituras válidas. Mudança engrenada e conta-rotações aparecem apenas como leituras brutas na secção em validação; não são usados como medições confirmadas.

## Evidências

Os 14 ficheiros estão em [`evidencias/`](evidencias/), com os nomes e bytes originais. As exportações mantêm a identificação da ferramenta que as produziu.

| Ficheiro | Conteúdo |
| --- | --- |
| [Diagnóstico de 8 de novembro](evidencias/openlauncher-can-diagnostics.txt) | Estado inicial e tramas históricas |
| [Diagnóstico de 9 de novembro](evidencias/openlauncher-can-diagnostics-2026-11-09.txt) | Contrato do serviço, perfil e primeiras leituras |
| [Diagnóstico de 10 de novembro](evidencias/openlauncher-can-diagnostics-2026-11-10.txt) | Leituras tipadas e callbacks, usados na comparação com os ecrãs |
| [Arranque do motor](evidencias/openlauncher-can-diagnostics-2026-11-10-2302.txt) | Sessão com abertura da porta e arranque |
| [Perfil CAN](evidencias/bmw-mini-can-profile.jpg) | Seleção BMW MINI `135725059` |
| [Computador de bordo](evidencias/2026-11-10-trip-computer.jpg) | Autonomia de 227 km, consumo de 6,5 L/100 km e velocidade média de 38,5 km/h |
| [Óleo do motor](evidencias/2026-11-10-engine-oil.jpg) | Manutenção: abril de 2028 e 18 000 km restantes |
| [Líquido de travões](evidencias/2026-11-10-brake-fluid.jpg) | Manutenção: abril de 2028, sem distância |
| [Inspeção do veículo](evidencias/2026-11-10-vehicle-inspection.jpg) | Manutenção: abril de 2030 e 42 000 km restantes |
| [Pressão dos pneus](evidencias/2026-11-10-tyre-pressure.jpg) | Quatro rodas sem leitura, apresentadas como `-0.1` bar |
| [Informação do sistema](evidencias/2026-11-10-system-info.jpg) | Sistema, MCU e identificação da caixa CAN |
| [CAN às 22:16](evidencias/2026-11-10-can-debug-2216.jpg) | Tramas da caixa CAN e da central |
| [CAN às 22:17, captura A](evidencias/2026-11-10-can-debug-2217a.jpg) | Tramas da caixa CAN e da central |
| [CAN às 22:17, captura B](evidencias/2026-11-10-can-debug-2217b.jpg) | Tramas da caixa CAN e da central |

Os três logcats de sistema originais não estão incluídos por conterem dados pessoais.

### Integridade

Os hashes estão em [SHA256SUMS](evidencias/SHA256SUMS). Para os verificar num ambiente com `sha256sum`, executar a partir desta pasta:

```bash
cd evidencias
sha256sum -c SHA256SUMS
```

O [`.gitattributes`](evidencias/.gitattributes) preserva os bytes dos diagnósticos nos checkouts. As exportações contêm preenchimento NUL; usar `tr -d '\000'` antes de ferramentas que trabalham por linhas.
