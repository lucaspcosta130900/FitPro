# FitPro — Acompanhamento de Saúde & Nutrição

App Android nativo em Kotlin + Jetpack Compose com suporte adaptativo para **Samsung Galaxy ZFold 7** (tela externa e interna). Integrado com **Claude AI** (Anthropic) para consultas personalizadas com base nos seus dados.

---

## Funcionalidades

| Módulo | O que faz |
|---|---|
| **Dashboard** | Anéis de progresso de macros, resumo de calorias, treinos recentes, peso e IMC |
| **Diário Alimentar** | Registo de refeições por tipo (café, almoço, lanche, jantar, ceia), busca de alimentos, cálculo de macros em tempo real |
| **Treinos** | Calendário GitHub-style (heatmap 26 semanas), registo de sessões, sequência de dias |
| **Perfil** | Histórico de peso, % gordura, histórico de exames laboratoriais, metas diárias |
| **FitPro AI** | Chat com Claude Sonnet personalizado com seus dados atuais de macros, treinos e exames |

---

## Requisitos

| Item | Mínimo |
|---|---|
| Android Studio | Ladybug 2024.2.1+ (ou Meerkat) |
| JDK | 17 (bundled com Android Studio) |
| Android Gradle Plugin | 8.5.2 |
| Kotlin | 2.0.0 |
| SDK mínimo | API 26 (Android 8.0) |
| SDK alvo | API 35 (Android 15) |

---

## Como abrir e compilar

### 1. Clonar / copiar o projeto

```
Copie a pasta FitPro para:
Windows: C:\Users\SeuNome\Documents\PersonalProjects\FitPro
```

### 2. Abrir no Android Studio

1. **File → Open** → selecione a pasta `FitPro`  
2. Aguarde o sync do Gradle (pode levar 2–5 min na primeira vez — baixa ~500 MB de dependências)  
3. Se aparecer erro de JDK: **File → Project Structure → SDK Location → JDK Location** → selecione o JDK 17 bundled

### 3. Configurar o dispositivo (ZFold 7)

**Modo desenvolvedor:**
1. Configurações → Sobre o telefone → Informações do software  
2. Toque 7x em "Número de compilação"  
3. Volte a Configurações → Opções do desenvolvedor → ative "Depuração USB"

**Conectar via USB:**
```
adb devices    ← deve mostrar o serial do ZFold 7
```

### 4. Executar

Selecione `app` + seu dispositivo na barra e clique ▶ **Run**.

---

## Configurar a API do Claude (FitPro AI)

1. Acesse **console.anthropic.com** e gere uma chave API (`sk-ant-...`)  
2. No app: **Perfil → Configurações → Chave API Anthropic**  
3. Cole a chave e salve  
4. A chave é armazenada localmente em DataStore (não sai do dispositivo)

---

## Adaptação ZFold 7

O app usa `NavigationSuiteScaffold` do Material3 Adaptive, que detecta automaticamente o tamanho da janela:

| Estado do ZFold 7 | WindowSizeClass | Navegação |
|---|---|---|
| **Dobrado** (tela externa 6,3") | `Compact` | BottomNavigationBar |
| **Aberto** (tela interna 7,9") | `Expanded` | NavigationRail lateral |

Nenhuma configuração manual necessária — abre e fecha o ZFold e a navegação se adapta em tempo real.

---

## Banco de dados local

O app usa **Room (SQLite)** — todos os dados ficam no dispositivo:

- `food_items` — 32 alimentos brasileiros pré-populados  
- `meal_entries` — refeições registradas  
- `training_sessions` — histórico de treinos  
- `body_metrics` — histórico de peso e composição corporal  
- `lab_exams` — resultados de exames laboratoriais  
- `chat_messages` — histórico do chat com Claude  
- `user_goals` — metas diárias de macros (padrão: 2.052 kcal, 160g P, 207g C, 65g G)

---

## Publicar na Google Play

1. **Build → Generate Signed Bundle / APK**  
2. Escolha **Android App Bundle (.aab)** (recomendado pela Play)  
3. Crie ou use um keystore existente  
4. Acesse **play.google.com/console** → Criar app → Enviar o `.aab`  
5. Taxa única de cadastro: **US$ 25**

---

## Metas pré-configuradas (baseadas no perfil de Lucas)

```
Calorias: 2.052 kcal/dia  (déficit −20% do TDEE sedentário)
Proteína: 160g             (2,0g/kg de massa magra ~80kg)
Carboidratos: 207g
Gordura: 65g
Fibras: 35g
Água: 4,0L
```

Para alterar: **Perfil → ícone de engrenagem → Editar metas**.

---

## Estrutura do projeto

```
app/src/main/java/com/fitpro/
├── FitProApplication.kt          # Hilt @HiltAndroidApp
├── MainActivity.kt               # Edge-to-edge + ZFold WindowSizeClass
├── data/
│   ├── local/
│   │   ├── entity/Entities.kt    # Room entities (6 tabelas)
│   │   ├── entity/Converters.kt  # TypeConverters LocalDate/enums
│   │   ├── dao/Daos.kt           # Room DAOs com Flow
│   │   └── AppDatabase.kt        # Banco + seed 32 alimentos brasileiros
│   ├── remote/
│   │   └── AnthropicApiService.kt # Retrofit para Claude API
│   ├── preferences/
│   │   └── UserPreferencesRepository.kt  # DataStore
│   └── repository/
│       └── Repositories.kt       # 4 repositórios (Food, Training, Health, AI)
├── di/
│   └── AppModules.kt             # Hilt: DatabaseModule + NetworkModule
└── ui/
    ├── theme/Theme.kt            # Material3 dark/light com cores FitPro
    ├── navigation/AppNavigation.kt  # NavigationSuiteScaffold adaptativo
    ├── components/Components.kt  # MacroProgressRing, TrainingHeatmap, MacroBar
    └── screens/
        ├── dashboard/            # Dashboard + ViewModel
        ├── diary/                # Diário alimentar + ViewModel
        ├── training/             # Treinos + Heatmap + ViewModel
        ├── profile/              # Perfil, peso, exames + ViewModel
        └── ai/                   # Chat Claude + ViewModel
```

---

## Tecnologias

- **UI**: Jetpack Compose + Material3 Adaptive  
- **Arquitetura**: MVVM + Clean + Hilt  
- **Banco**: Room 2.6.1 + KSP  
- **API**: Retrofit 2.11 + OkHttp 4.12  
- **Estado**: Kotlin Flows + StateFlow  
- **Preferências**: DataStore 1.1  
- **IA**: Anthropic Claude claude-sonnet-4-5  
- **Gráficos**: Canvas customizado (heatmap) + Vico 2.0 (próxima versão)
