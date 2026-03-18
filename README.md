# ParserNG 🧮⚡

**ParserNG 1.0.0** is a **blazing-fast**, **pure Java**, **zero-native-dependencies** math expression parser and evaluator.

It **beats Janino, exp4J, and JavaMEP on evaluation speed** across every kind of expression — from simple algebra to heavy trig, matrices, and calculus.  
With the new **Turbo compiled mode**, it routinely reaches **3–10 million evaluations per second**.

It goes far beyond basic parsing — offering **symbolic differentiation**, **resilient numerical integration**, **full matrix algebra**, **statistics**, **equation solving**, **user-defined functions**, **2D graphing**, and more — all in one lightweight, cross-platform library.

Perfect for scientific computing, simulations, real-time systems, education tools, Android apps, financial models, and high-performance scripting.

[![Maven Central](https://img.shields.io/maven-central/v/com.github.gbenroscience/parser-ng.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/com.github.gbenroscience/parser-ng)
[![License](https://img.shields.io/github/license/gbenroscience/ParserNG?color=blue)](https://github.com/gbenroscience/ParserNG/blob/master/LICENSE)
![Java](https://img.shields.io/badge/Java-8%2B-orange)
![Latest Version](https://img.shields.io/badge/version-1.0.0-success)

> **1.0.0** introduces **Turbo Scalar** and **Turbo Matrix** compiled paths + massive speed improvements via strength reduction, constant folding, and O(1) frame-based argument passing.

## ✨ Highlights (v1.0.0)

- **Speed champion** — beats Janino, exp4J, and JavaMEP in every benchmark (see [BENCHMARK_RESULTS.md](BENCHMARK_RESULTS.md))
- **Turbo Mode** — compile once, evaluate millions of times per second (Scalar + Matrix paths)
- Symbolic differentiation (`diff`) + resilient numerical integration (`intg`) that handles difficult expressions
- Full matrix algebra (`det`, `eigvalues`, `eigvec`, `adjoint`, `cofactor`, matrix division, `linear_sys`, …)
- Statistics (`avg`, `variance`, `cov`, `min`, `max`, `rms`, `listsum`, `sort`, …)
- Equation solvers: quadratic, **Tartaglia cubic**, numerical roots, linear systems
- User-defined functions (`f(x)=…` or lambda `@(x,y)…`) + persistent `FunctionManager` / `VariableManager`
- Variables with execution frames for ultra-fast loops
- 2D function & geometric plotting support
- Logical expressions (`and`, `or`, `==`, …)
- No external dependencies — runs on Java SE, Android, JavaME, …

## 🚀 Installation (Maven)

```xml
<dependency>
    <groupId>com.github.gbenroscience</groupId>
    <artifactId>parser-ng</artifactId>
    <version>1.0.0</version>
</dependency>
```

Also available on **Maven Central**:  
https://central.sonatype.com/artifact/com.github.gbenroscience/parser-ng/1.0.0

## ⚡ Turbo Mode — The 1.0.0 Game Changer

```java
import com.github.gbenroscience.parser.MathExpression;
import com.github.gbenroscience.parser.turbo.tools.FastCompositeExpression;
```

### Turbo Scalar (tight loops / millions per second)

```java
String expr = "x*sin(x) + y*cos(y) + z^2";
MathExpression me = new MathExpression(expr, false);      // prepare for turbo
FastCompositeExpression turbo = me.compileTurbo();        // compile once

int xSlot = me.getVariable("x").getFrameIndex();
int ySlot = me.getVariable("y").getFrameIndex();
int zSlot = me.getVariable("z").getFrameIndex();

double[] frame = new double[3];

for (double t = 0; t < 10_000_000; t += 0.001) {
    frame[xSlot] = t;
    frame[ySlot] = t * 1.5;
    frame[zSlot] = t / 2.0;
    double result = turbo.applyScalar(frame);   // ← ultra-fast!
}
```

### Turbo Matrix (eigvalues, linear systems, etc.)

```java
MathExpression me = new MathExpression("eigvalues(R)");
FastCompositeExpression turbo = TurboEvaluatorFactory.getCompiler(me).compile();

Matrix result = turbo.applyMatrix(new double[0]);   // works for: linear_sys, adjoint, cofactor, A/B, etc.
```

## Quick Start (Normal Mode + Turbo)

### 1. Simple expression

```java
MathExpression expr = new MathExpression("r = 5; 2 * pi * r");
System.out.println(expr.solve());   // ≈ 31.4159
```

### 2. High-performance loop (Turbo recommended)

```java
MathExpression expr = new MathExpression("x^2 + 5*x + sin(x)", false);
FastCompositeExpression turbo = expr.compileTurbo();
int xSlot = expr.getVariable("x").getFrameIndex();
double[] frame = new double[1];

for (double x = 0; x < 100_000; x += 0.1) {
    frame[xSlot] = x;
    double y = turbo.applyScalar(frame);
    // plot or process y
}
```

### 3. Symbolic derivative

```java
MathExpression expr = new MathExpression("f(x) = x^3 * ln(x); diff(f, 2, 1)");
System.out.println(expr.solveGeneric().scalar);
```

### 4. Numerical integration (even difficult ones work in Turbo)

```java
MathExpression expr = new MathExpression("intg(@(x) 1/(x*sin(x)+3*x*cos(x)), 0.5, 1.8)");
System.out.println("∫ ≈ " + expr.solve());
```

### 5. Matrix example

```java
MathExpression expr = new MathExpression("""
    M = @(3,3)(1,2,3, 4,5,6, 7,8,9);
    det(M)
""");
System.out.println("Determinant = " + expr.solve());
```

## ⌨️ Command-line tool (REPL)

```bash
java -jar parser-ng-1.0.0.jar "sin(x) + cos(x)"
java -jar parser-ng-1.0.0.jar "eigvalues(R=@(5,5)(...))"
java -jar parser-ng-1.0.0.jar help
java -jar parser-ng-1.0.0.jar -i          # interactive mode
```

## 📊 Supported Features at a Glance

| Category          | Key Functions                                      | Turbo Support |
|-------------------|----------------------------------------------------|---------------|
| Arithmetic & Logic| `+ - * / ^ % and or == !=`                         | Full          |
| Trigonometry      | `sin cos tan asin … sinh`                          | Full          |
| Calculus          | `diff` (symbolic), `intg` (resilient)              | Yes           |
| Equations         | Quadratic, Tartaglia cubic, `root`, `linear_sys`   | Yes           |
| Matrices          | `det`, `eigvalues`, `eigvec`, `adjoint`, `cofactor`, `A / B` | Excellent (Turbo Matrix) |
| Statistics        | `avg variance cov min max rms listsum sort`        | Yes           |
| Custom            | `@(x,y)…` or named functions                       | Full          |

Full list: run `help` or `new MathExpression("help").solve()`.

## 📚 More Documentation

- [BENCHMARK_RESULTS.md](BENCHMARK_RESULTS.md) — full speed comparisons
- [GRAPHING.md](GRAPHING.md) — plotting on Swing / JavaFX / Android
- [LATEST.md](LATEST.md) — what’s new in 1.0.0
- Javadoc: https://javadoc.io/doc/com.github.gbenroscience/parser-ng/1.0.0

## ❤️ Support the Project

ParserNG is built with love in my free time. If it helps you:

- ⭐ Star the repository  
- 🐞 Report bugs or suggest features  
- 💡 Share what you built with it  
- ☕ [Buy me a coffee](https://buymeacoffee.com/gbenroscience)

## 📄 License

**Apache License 2.0**

---

**ParserNG 1.0.0** — faster than the competition, stronger on matrices, and now with real Turbo Scalar + Turbo Matrix compiled power.

Happy parsing! 🚀  
— **GBENRO JIBOYE** (@gbenroscience)