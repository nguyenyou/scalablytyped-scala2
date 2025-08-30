# ScalablyTyped Scala to TypeScript Migration Plan

## Executive Summary

This document outlines a comprehensive strategy to port the ScalablyTyped converter from Scala to TypeScript. ScalablyTyped is a sophisticated tool that converts TypeScript definition files (.d.ts) into Scala.js type definitions, enabling type-safe JavaScript library usage in Scala.js projects.

## 1. Codebase Analysis

### Architecture Overview
The system follows a **multi-phase pipeline architecture** with these key modules:

- **`core`** - Fundamental data structures (IArray, Name, QualifiedName)
- **`logging`** - Logging infrastructure and utilities  
- **`ts`** - TypeScript AST representation and parsing logic
- **`scalajs`** - Scala.js AST representation and code generation
- **`phases`** - Pipeline framework for multi-stage processing
- **`importer-portable`** - Core conversion logic and phases
- **`importer`** - Full importer with CI/CD capabilities
- **`cli`** - Command-line interfaces
- **`runtime`** - Scala.js runtime components

### Data Flow Pipeline
```
LibTsSource → LibTs → LibScalaJs → PublishedSbtProject
     ↓           ↓         ↓              ↓
  Parse TS → Transform → Convert → Compile & Package
```

### Key External Dependencies
- **Circe** (JSON processing) → Replace with native TypeScript JSON
- **OS-Lib** (File operations) → Replace with Node.js fs/path
- **Ammonite** (Scripting) → Replace with Node.js scripting
- **Coursier** (Dependency resolution) → Replace with npm/package.json
- **Mill** (Build system) → Replace with TypeScript build tools
- **utest** (Testing) → Replace with Jest/Vitest

## 2. Migration Strategy

### Phase 1: Foundation & Analysis
**Estimated Effort: 2-3 weeks**

#### 1.1 Module Dependency Analysis
- Map dependency graph between all modules
- Identify circular dependencies and resolution strategies
- Document module interfaces and contracts

#### 1.2 Scala Feature Catalog
- **Case Classes** → TypeScript interfaces with discriminated unions
- **Pattern Matching** → Switch statements with type guards
- **Implicits** → Explicit parameter passing or dependency injection
- **Traits** → TypeScript interfaces and mixins
- **Sealed Traits** → Discriminated union types
- **Option/Either** → Custom monadic types or fp-ts library

#### 1.3 TypeScript Project Setup
```typescript
// Project structure
src/
├── core/           // Core data structures
├── logging/        // Logging infrastructure
├── ts/            // TypeScript AST
├── scalajs/       // Scala.js AST
├── phases/        // Pipeline framework
├── importer/      // Import logic
└── cli/           // Command line tools

// Build configuration
- TypeScript 5.x with strict mode
- ESM modules with Node.js 18+
- Vitest for testing
- ESLint + Prettier for code quality
```

### Phase 2: Core Infrastructure Migration
**Estimated Effort: 3-4 weeks**

#### 2.1 Immutable Collections (IArray)
```typescript
// Scala IArray equivalent
class IArray<T> {
  private readonly items: readonly T[];
  
  static empty<T>(): IArray<T> { return new IArray([]); }
  static of<T>(...items: T[]): IArray<T> { return new IArray(items); }
  
  map<U>(fn: (item: T) => U): IArray<U> { /* ... */ }
  filter(predicate: (item: T) => boolean): IArray<T> { /* ... */ }
  // ... other immutable operations
}
```

#### 2.2 Naming System
```typescript
// Name and QualifiedName system
interface Name {
  readonly value: string;
  readonly isValid: boolean;
}

interface QualifiedName {
  readonly parts: IArray<Name>;
  readonly isGlobal: boolean;
}
```

#### 2.3 JSON Handling
Replace Circe with TypeScript-native JSON handling:
```typescript
// Type-safe JSON encoding/decoding
interface JsonEncoder<T> {
  encode(value: T): unknown;
}

interface JsonDecoder<T> {
  decode(json: unknown): T | Error;
}
```

### Phase 3: TypeScript AST & Parsing
**Estimated Effort: 4-5 weeks**

#### 3.1 AST Data Structures
```typescript
// TypeScript AST node types
type TsTree = TsType | TsDecl | TsMember | TsContainer;

interface TsType {
  readonly kind: 'type';
}

type TsTypeRef = TsType & {
  readonly kind: 'typeRef';
  readonly name: TsQIdent;
  readonly targs: IArray<TsType>;
};

// Union types for all AST variants
type TsDecl = TsDeclClass | TsDeclInterface | TsDeclFunction | /* ... */;
```

#### 3.2 Parser Integration
Integrate with TypeScript Compiler API:
```typescript
import * as ts from 'typescript';

interface TsParser {
  parseFile(filePath: string): TsParsedFile | Error;
  parseString(content: string): TsParsedFile | Error;
}
```

#### 3.3 Tree Transformations
```typescript
// Visitor pattern for AST transformations
abstract class TreeTransformation {
  abstract visitTsType(node: TsType): TsType;
  abstract visitTsDecl(node: TsDecl): TsDecl;
  
  // Composition operator
  compose(other: TreeTransformation): TreeTransformation;
}
```

### Phase 4: Scala.js AST & Code Generation
**Estimated Effort: 4-5 weeks**

#### 4.1 Scala.js AST
```typescript
// Scala.js AST representation
type Tree = ClassTree | MethodTree | PackageTree | /* ... */;

interface ClassTree extends Tree {
  readonly kind: 'class';
  readonly name: Name;
  readonly tparams: IArray<TypeParamTree>;
  readonly parents: IArray<TypeRef>;
  readonly members: IArray<Tree>;
}
```

#### 4.2 Type System & Erasure
```typescript
// Type erasure for method signature collision detection
interface Erasure {
  eraseMethod(method: MethodTree): string;
  eraseType(typeRef: TypeRef): string;
}
```

#### 4.3 Code Generation
```typescript
// Scala source code generation
interface ScalaCodeGenerator {
  generatePackage(pkg: PackageTree): string;
  generateClass(cls: ClassTree): string;
  generateMethod(method: MethodTree): string;
}
```

### Phase 5: Pipeline & Transformation Engine
**Estimated Effort: 3-4 weeks**

#### 5.1 Phase Framework
```typescript
// Multi-phase pipeline system
interface Phase<TIn, TOut> {
  apply(input: TIn, deps: Dependencies): Promise<TOut | Error>;
}

class RecPhase<TId, T> {
  next<TOut>(phase: Phase<T, TOut>, name: string): RecPhase<TId, TOut>;
}
```

#### 5.2 Dependency Resolution
```typescript
// Dependency management between phases
interface GetDeps<TSource, TResult> {
  (sources: Set<TSource>): Promise<Map<TSource, TResult>>;
}
```

### Phase 6: Import Pipeline & Business Logic
**Estimated Effort: 5-6 weeks**

#### 6.1 Core Conversion Phases
- **Phase1ReadTypescript** - Parse and transform TypeScript
- **Phase2ToScalaJs** - Convert to Scala.js AST
- **PhaseFlavour** - Apply flavour-specific transformations
- **Phase3Compile** - Generate final output

#### 6.2 Library Resolution
```typescript
// NPM package and DefinitelyTyped integration
interface LibraryResolver {
  resolveLibrary(name: string): LibTsSource | Error;
  findDependencies(lib: LibTsSource): Set<LibTsSource>;
}
```

### Phase 7: CLI & Build System
**Estimated Effort: 2-3 weeks**

#### 7.1 Command Line Interface
```typescript
// CLI commands equivalent to Scala versions
interface CliCommand {
  name: string;
  description: string;
  execute(args: string[]): Promise<number>;
}

// Main commands:
// - convert (equivalent to Main.scala)
// - source-only (equivalent to SourceOnlyMain.scala)
// - import-definitions (equivalent to ImportScalajsDefinitions.scala)
```

#### 7.2 Build Integration
Replace Mill with TypeScript build tools:
- **Package.json** scripts for common tasks
- **TSC** for compilation
- **Vitest** for testing
- **ESBuild/Rollup** for bundling

### Phase 8: Testing & Validation
**Estimated Effort: 3-4 weeks**

#### 8.1 Test Migration Strategy
```typescript
// Port existing utest tests to Vitest
describe('TypeScript Parser', () => {
  test('should parse simple interface', () => {
    const input = 'interface User { id: number; }';
    const result = parser.parseString(input);
    expect(result).toMatchSnapshot();
  });
});
```

#### 8.2 Integration Testing
- **Golden file testing** - Compare outputs with original Scala implementation
- **Performance benchmarks** - Ensure comparable performance
- **Real-world library testing** - Test with popular npm packages

## 3. Technical Considerations

### 3.1 Scala to TypeScript Mapping

| Scala Feature | TypeScript Equivalent | Notes |
|---------------|----------------------|-------|
| Case Classes | Interfaces + Factories | Use readonly properties |
| Sealed Traits | Discriminated Unions | Use literal type discrimination |
| Pattern Matching | Type Guards + Switch | Leverage TypeScript's type narrowing |
| Implicits | Explicit Parameters | Or dependency injection pattern |
| Option[T] | T \| undefined | Or custom Option monad |
| Either[L,R] | Custom Either type | Use fp-ts or custom implementation |

### 3.2 Performance Considerations
- **Immutable data structures** - Use libraries like Immutable.js or custom implementations
- **Memory management** - Careful object lifecycle management
- **Streaming processing** - For large TypeScript definition files
- **Caching** - Maintain parsing and transformation caches

### 3.3 Error Handling Strategy
```typescript
// Consistent error handling pattern
type Result<T, E = Error> = { success: true; value: T } | { success: false; error: E };

// Or use fp-ts Either
import { Either, left, right } from 'fp-ts/Either';
```

## 4. Risk Mitigation

### 4.1 High-Risk Areas
1. **Complex TypeScript transformations** - QualifyReferences, FlattenTrees
2. **Scala.js type erasure logic** - Method signature collision detection
3. **Module system handling** - CommonJS/ES module resolution
4. **Performance-critical paths** - Large library processing

### 4.2 Mitigation Strategies
- **Incremental migration** - Port and validate one module at a time
- **Comprehensive testing** - Golden file tests against original implementation
- **Performance monitoring** - Benchmark critical paths
- **Fallback mechanisms** - Graceful degradation for edge cases

## 5. Success Criteria

### 5.1 Functional Requirements
- [ ] Successfully convert popular TypeScript libraries (React, Lodash, etc.)
- [ ] Generate equivalent Scala.js bindings to original implementation
- [ ] Support all major TypeScript language features
- [ ] Maintain compatibility with existing ScalablyTyped workflows

### 5.2 Non-Functional Requirements
- [ ] Performance within 20% of original Scala implementation
- [ ] Memory usage comparable to original
- [ ] Comprehensive test coverage (>90%)
- [ ] Clear documentation and migration guides

## 6. Timeline & Milestones

**Total Estimated Duration: 26-34 weeks (6-8 months)**

| Phase | Duration | Key Deliverables |
|-------|----------|------------------|
| 1 | 2-3 weeks | Architecture analysis, TypeScript project setup |
| 2 | 3-4 weeks | Core infrastructure (IArray, Name, JSON, logging) |
| 3 | 4-5 weeks | TypeScript AST and parsing |
| 4 | 4-5 weeks | Scala.js AST and code generation |
| 5 | 3-4 weeks | Pipeline framework |
| 6 | 5-6 weeks | Import pipeline and business logic |
| 7 | 2-3 weeks | CLI and build system |
| 8 | 3-4 weeks | Testing and validation |

## 7. Next Steps

1. **Validate approach** - Review plan with stakeholders
2. **Set up development environment** - TypeScript toolchain
3. **Begin Phase 1** - Start with module dependency analysis
4. **Establish testing strategy** - Set up comparison framework
5. **Create migration tracking** - Progress monitoring system

This migration plan provides a systematic approach to porting ScalablyTyped while maintaining functionality and enabling incremental validation at each stage.
