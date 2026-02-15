# JFocus 🔍

[![Java 21](https://img.shields.io/badge/Java-21-orange?logo=java)](https://openjdk.org/projects/jdk/21/)
[![Gradle](https://img.shields.io/badge/Gradle-8.x-02303A?logo=gradle)](https://gradle.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

[🇺🇸 **English**](README.md) | [🇰🇷 **Korean(한국어)**](README.ko.md)

> **"AI에게 전체 파일을 그만 붙여넣으세요. 필요한 건 오직 문맥입니다."**
>
> JFocus는 방대한 자바 프로젝트에서 **필요한 코드만 똑똑하게** 골라내어, ChatGPT나 Claude 같은 LLM에게 **최적의 프롬프트**를 제공할 수 있도록 돕는 CLI 도구입니다.

---

## 📝 목차

- [소개](#소개-introduction)
- [효과 (Benchmarks)](#효과-benchmarks)
- [주요 기능](#주요-기능-features)
- [설치 방법](#설치-방법-installation)
- [사용 방법](#사용-방법-usage)
- [For AI Agents](#for-ai-agents-cursor-windsurf)
- [기여하기](#기여하기-contributing)
- [라이선스](#라이선스-license)

---

## 💡 소개 (Introduction)
![JFocus CLI Demo](src/docs/images/demo.png)

대규모 자바 프로젝트를 개발하거나 분석할 때, LLM에게 코드를 이해시키기 위해 전체 파일을 복사해 붙여넣는 것은 비효율적입니다. 토큰 제한에 걸리거나, 불필요한 정보로 인해 LLM의 답변 품질이 떨어질 수 있습니다.

**JFocus**는 이러한 문제를 해결합니다. 사용자가 분석하고자 하는 특정 메서드를 선택하면, 해당 메서드가 의존하는 **필수적인 문맥(변수, 호출된 메서드, 클래스 구조 등)**만을 분석하여 마크다운 형태로 추출합니다.

### 🎯 언제 JFocus를 써야 하나요? (Use Cases)

- **특정 메서드 하나만 집중 분석할 때**: 파일 전체를 넣기엔 너무 크고, 메서드만 넣기엔 문맥이 부족할 때
- **거대 클래스(God Class)를 다룰 때**: 500줄이 넘는 레거시 코드에서, 내가 수정할 부분과 연관된 로직만 쏙 뽑아내고 싶을 때
- **의존성이 복잡하게 얽혀 있을 때**: `this.validate()`, `service.process()` 등 내부/외부 호출 관계를 한 눈에 파악해야 할 때
- **LLM과 함께 리팩토링/디버깅할 때**: AI에게 "이 메서드랑 관련된 것만 보고 조언해줘"라고 하고 싶을 때

### 🚫 JFocus가 하지 않는 것 (Non-Goals)

- **런타임 분석을 하지 않습니다**: 실제 실행 시점의 데이터 흐름이나 리플렉션, AOP 등은 추적하지 않습니다.
- **외부 라이브러리를 분석하지 않습니다**: 프로젝트 내의 소스 코드(.java)만 분석하며, Spring Bean 그래프나 JAR 내부의 코드는 들여다보지 않습니다.
- **IDE를 대체하지 않습니다**: 전체 프로젝트 탐색은 IDE가 훨씬 강력합니다. JFocus는 **"프롬프트 생성"**에만 집중합니다.

### 🛡️ 왜 정규표현식이 아닌 AST인가요? (Why AST?)

"그냥 `grep`이나 정규식으로 찾으면 되지 않나요?"

텍스트 기반 검색은 메서드 오버로딩, 내부 클래스, 동명이인(같은 이름의 다른 메서드)을 구분하지 못해 **잘못된 문맥(Hallucination Context)**을 LLM에게 주입할 위험이 큽니다.

**JFocus**는 JavaParser를 사용하여 코드를 **추상 구문 트리(AST)**로 변환하고 분석합니다.
- **정확한 참조 추적**: 문자열 일치가 아닌, **Symbol Resolution**을 통해 실제 심볼이 가리키는 대상을 정확히 추적합니다.
- **노이즈 제거**: 주석, import 구문 등 불필요한 토큰을 배제하고 로직에만 집중합니다.

---

## 📊 효과 (Benchmarks)

`jfocus`는 LLM 에이전트의 컨텍스트 사용량을 획기적으로 최적화합니다. 타겟 메서드의 로직과 참조된 의존성의 서명(Signature)만 추출하여, 토큰 소비를 최소화하면서도 코드 이해에 필요한 충분한 문맥을 유지합니다.

**테스트 환경:**
- **Target:** Production-level Java Spring Boot Project
- **Metric:** Character count comparison (Raw File vs. `jfocus` Output)

| Component Type | File Name | Raw Size (Chars) | jfocus Output (Chars) | **Reduction Rate** |
| :--- | :--- | :--- | :--- | :--- |
| **Controller** | `Controller.java` | 11,705 | ~2,400 | **🔻 79.5%** |
| **Logic (Mid)** | `Service.java` | 4,913 | ~2,400 | **🔻 51.1%** |
| **Logic (Small)** | `SimpleService.java` | 2,304 | ~2,014 | **🔻 12.6%** |
| **Entity** | `Entity.java` | 1,728 | ~225 | **🔻 87.0%** |

> **Key Findings:**
> - **거대한 파일에서 압도적 절감:** 복잡한 컨트롤러나 서비스 클래스에서 **최대 80%**까지 용량을 줄여, 동일한 컨텍스트 윈도우 내에서 5배 더 많은 파일을 처리할 수 있습니다.
> - **작은 파일도 놓치지 않는 문맥:** 절감률이 낮은(12%) 작은 파일이라도, 원본 파일에는 없는 **외부 의존성 정보(Dependency Signatures)**를 포함하여 에이전트에게 더 풍부한 정보를 제공합니다.
> 
> *측정 기준: GPT-4o Tokenizer (근사치), 실제 토큰 수는 사용하는 모델 및 토크나이저에 따라 달라질 수 있습니다.*
>
> **💡 정성적 효과 (Qualitative Result):**
> 실제 사용 결과, LLM의 응답이 훨씬 **명확해졌으며(Focused)**, 관련 없는 메서드를 참조하여 발생하는 **환각(Hallucination)이 현저히 감소**했습니다.

### 🚀 확장성 및 성능 (Scalability)

> "수천 개의 클래스가 있는 모노레포에서도 작동하나요?"

네, 가능은 하지만 **단일 모듈(Single Module)** 또는 **모놀리식(Monolithic)** 구조에 최적화되어 있습니다.

- **✅ Single Module**: 완벽한 심볼 추적과 의존성 분석을 지원합니다.
- **⚠️ Multi-Module**: 멀티모듈 프로젝트도 지원하지만, 클래스명이 모듈 간에 **유니크할 때 최적의 정확도**를 보장합니다. (타 모듈 클래스는 파일명 매칭 방식을 사용합니다.)


---

## ✨ 주요 기능 (Features)

- **🎯 정밀한 문맥 추출**: 
  - 메서드 내부에서 호출되는 다른 메서드, 필드 변수, 상속 구조 등을 재귀적으로 분석하지 않고, **직접적인 연관성**을 파악하여 핵심 정보만 제공합니다.
  - (Deep Dive 옵션을 통해 깊이 있는 분석 지원 예정)
  
- **🖥️ 대화형 CLI (Interactive Mode)**:
  - 복잡한 경로를 입력할 필요 없이, 파일명만 입력하면 해당 파일 내의 메서드 목록을 보여주고 선택할 수 있습니다.

- **📋 클립보드 자동 복사**:
  - `-c` 또는 `--copy` 옵션으로 추출된 결과를 즉시 클립보드에 저장하여, LLM 채팅창에 바로 붙여넣을 수 있습니다.

- **🧩 참조 코드 포함 분석**:
  - `-v` 또는 `--verbose` 옵션을 사용하면, 분석 중인 메서드가 **직접 참조하는(Directly Referenced)** 메서드의 구현 코드를 포함합니다.
  - (프로젝트 내 코드에 한하며, 깊은 의존성 체인을 무조건적으로 재귀 확장하지는 않습니다.)

---

## 📦 설치 방법 (Installation)

### 사전 준비

JFocus를 실행하려면 **Java 21 이상**이 필요합니다.

```bash
java -version  # Java 21 이상인지 확인
```

> Java가 설치되어 있지 않다면: [Oracle JDK](https://www.oracle.com/java/technologies/downloads/) 또는 [OpenJDK](https://openjdk.org/) 다운로드

---

### 자동 설치 (권장)

설치 스크립트가 다음을 자동으로 처리합니다:
1. ✅ GitHub Releases에서 최신 빌드된 JAR 다운로드
2. ✅ SHA256 체크섬으로 파일 무결성 검증
3. ✅ `~/.jfocus/` 디렉토리에 설치
4. ✅ 실행 스크립트 생성

#### macOS / Linux (One-Line Install)

설치와 함께 `jfocus` 명령어를 사용할 수 있도록 Alias를 자동으로 등록합니다.

```bash
# ⚠️ 보안이 우려된다면 실행 전 스크립트 내용을 확인하세요.
curl -sL https://raw.githubusercontent.com/jher235/j-focus/main/scripts/install.sh | bash
```

#### Windows (PowerShell)

관리자 권한 없이도 실행 가능합니다. 설치 후 환경 변수(PATH)까지 자동으로 설정합니다.

```powershell
iwr -useb https://raw.githubusercontent.com/jher235/j-focus/main/scripts/install.ps1 | iex
```

---

### 설치 확인

```bash
jfocus --version
# 출력: JFocus v1.0.0
```

---

<details>
<summary>📂 수동 설치 (Manual Installation)</summary>

자동 스크립트 없이 직접 설치하려면:

1. **JAR 다운로드:**
   - [Releases 페이지](https://github.com/jher235/j-focus/releases)에서 `j-focus-1.0.0-all.jar` 다운로드

2. **설치:**
   ```bash
   mkdir -p ~/.jfocus
   mv j-focus-1.0.0-all.jar ~/.jfocus/j-focus.jar
   ```

3. **Alias 설정 (Linux/macOS):**
   ```bash
   echo "alias jfocus='java -jar ~/.jfocus/j-focus.jar'" >> ~/.zshrc
   source ~/.zshrc
   ```

4. **Windows:**
   - `C:\Users\사용자명\.jfocus\` 폴더 생성
   - JAR 파일 이동
   - `jfocus.bat` 파일 생성:
     ```bat
     @echo off
     java -jar "%USERPROFILE%\.jfocus\j-focus.jar" %*
     ```
   - PATH에 `%USERPROFILE%\.jfocus` 추가를 권장합니다.
</details>

---

### 문제 해결

#### "java: command not found"
→ Java가 설치되지 않았거나 PATH에 없습니다.
```bash
# macOS (Homebrew)
brew install openjdk@21

# Ubuntu/Debian
sudo apt install openjdk-21-jdk

# Windows
# Oracle JDK 또는 OpenJDK 설치 후 환경 변수 설정
```

#### "jfocus: command not found" (설치 후)
→ Alias가 등록되지 않았습니다. 위의 "설치 후 alias 추가" 단계를 다시 실행하세요.

#### Windows에서 "보안 경고" 발생
→ PowerShell 실행 정책 문제입니다.
```powershell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```




---

## 🛠️ 개발자용 (소스에서 빌드)

프로젝트에 기여하거나 최신 개발 버전을 테스트하려면:

```bash
# 1. 저장소 클론
git clone https://github.com/jher235/j-focus.git
cd j-focus

# 2. 빌드 (Gradle Wrapper 사용 - Gradle 설치 불필요)
./gradlew clean shadowJar

# 3. 실행
java -jar build/libs/j-focus-*-all.jar --version
```

**로컬 개발 환경:**
```bash
# Alias로 등록 (개발 중인 JAR 직접 실행)
alias jfocus-dev='java -jar ~/projects/j-focus/build/libs/j-focus-*-all.jar'
```

---

## 🎮 사용 방법 (Usage)

### 기본 실행
설치가 완료되면 `jfocus` 명령어로 어디서든 실행할 수 있습니다. 파일명이나 메서드명을 인자로 주지 않으면 **대화형 모드**가 시작됩니다.

```bash
jfocus
```

### CLI 옵션 (Options)

```bash
Usage: jfocus [-cvhV] [fileName] [methodName]
```

| 옵션 | 설명 | 예시 |
|------|------|------|
| `[fileName]` | 분석할 자바 파일명 (확장자 생략 가능) | `UserController` |
| `[methodName]` | 분석할 메서드명 | `login` |
| `-c`, `--copy` | 결과를 터미널에 출력하는 대신 **클립보드에 복사**합니다. | `jfocus -c` |
| `-v`, `--verbose` | **직접 참조된** 다른 메서드의 소스 코드를 포함합니다. (깊은 재귀 탐색 제외) | `jfocus -v` |
| `-h`, `--help` | 도움말 메시지를 표시합니다. | |
| `-V`, `--version` | 버전 정보를 표시합니다. | |

### 사용 예시 (Scenario)
 
 **시나리오**: `ContextExtractor.java` 파일의 `extractContext` 메서드를 분석하여 LLM에게 질문하고 싶을 때
 
 1. **명령어 실행**:
    ```bash
    jfocus conte
    ```
 2. **메서드 선택 (대화형)**:
    ```text
    Searching for: conte...
    Source Root configured: C:\open_source\j-focus\src\main\java
    Ambiguous file name. Found 3 matches:
       [1] ContextResult.java             (src/main/java/com/jher235/jfocus/model)
       [2] ContextExtractor.java          (src/main/java/com/jher235/jfocus/core)
       [3] ContextExtractorTest.java      (src/test/java/com/jher235/jfocus/core)
    Select (1-3): 2
    Found File: ...\src\main\java\com\jher235\jfocus\core\ContextExtractor.java
 
    Available Methods:
     [1] extractContext(MethodDeclaration targetMethod)
     [2] extractRecursive(MethodDeclaration rootTarget, ... )
     
    Select method number: 1
    ```
 3. **결과 확인**: "-c" 옵션을 썼다면 클립보드에, 아니면 화면에 결과가 출력됩니다.
 
### 📄 출력 결과 예시 (Output Example)

> 생성된 마크다운은 **ChatGPT나 Claude에 그대로 붙여넣어도 안전합니다.** (Safe to paste)
 
 `jfocus`가 생성하는 실제 마크다운 결과입니다. (펼쳐서 확인)
 
 <details>
 <summary><strong>🔎 ContextExtractor.extractContext() 분석 결과 보기</strong></summary>
 
 ````markdown
 # Target Method
 The main logic to analyze.
 
 ```java
 /**
  * Extracts the full context for the given target method recursively.
  * ...
  */
 public ContextResult extractContext(MethodDeclaration targetMethod) {
     ContextResult result = new ContextResult(targetMethod);
     // Fields
     result.setUsedFields(dependencyResolver.resolveFields(targetMethod));
     // Track visited methods to prevent infinite loops during recursion
     Set<String> visited = new HashSet<>();
     visited.add(AstUtils.createMethodId(targetMethod));
     // Start recursive analysis
     extractRecursive(targetMethod, targetMethod, result, visited);
     return result;
 }
 ```
 
 
 ## Internal Context (Same Class)
 Methods called by the target, defined within the same class.
 
 ```java
 /**
  * Recursively traverses method calls to find all related user code.
  * External libraries are automatically excluded as they lack source code definitions.
  */
 private void extractRecursive(MethodDeclaration rootTarget, MethodDeclaration currentMethod, ContextResult result, Set<String> visited) {
     List<MethodDeclaration> dependencies = dependencyResolver.resolveMethods(currentMethod);
     // ... (생략: 재귀적 탐색 로직) ...
 }
 ```
 
 ## Related Fields
 Class fields accessed by the target method.
 
 ```java
 private final DependencyResolver dependencyResolver;
 ```



````

</details>

---

## 🤖 For AI Agents (Cursor, Windsurf)

**JFocus**는 AI 에이전트(Cursor, Windsurf)와 결합했을 때 가장 강력합니다. 매번 프롬프트를 입력할 필요 없이, 프로젝트 설정 파일에 규칙을 추가하여 **에이전트가 스스로 도구를 사용하도록** 만드세요.


### 1. 에이전트 규칙 설정 (Configure Agent Rules)

프로젝트 루트의 에이전트 설정 파일(예: `.cursorrules`, `.windsurfrules` 등)에 [src/main/java/com/jher235/jfocus/docs/rules.md](src/main/java/com/jher235/jfocus/docs/rules.md) 파일의 내용을 복사해 붙여넣으세요.

### 2. 사용 예시

이제 에이전트에게 자연스럽게 질문하세요:

> "이 프로젝트의 `PaymentService.process()` 메서드를 분석해서 리팩토링 제안해줘."

에이전트는 자동으로 `jfocus`를 실행하여 문맥을 파악한 뒤, 정확한 답변을 제공할 것입니다.

---

## 🤝 기여하기 (Contributing)

이 프로젝트는 오픈 소스이며, 여러분의 기여를 환영합니다! 🎉

1. 이 저장소를 **Fork** 하세요.
2. 새로운 기능 브랜치를 생성하세요 (`git checkout -b feature/amazing-feature`).
3. 변경 사항을 커밋하세요 (`git commit -m 'Add some amazing feature'`).
4. 브랜치에 푸시하세요 (`git push origin feature/amazing-feature`).
5. **Pull Request**를 열어주세요.

버그 제보나 기능 제안은 [Issues](https://github.com/jher235/jfocus/issues) 탭을 이용해 주세요.

---

## 📜 라이선스 (License)

이 프로젝트는 **MIT License**에 따라 배포됩니다. 자세한 내용은 `LICENSE` 파일을 참고하세요.