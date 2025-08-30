# Phase2ToScalaJs Technical Documentation

## Overview

`Phase2ToScalaJs.scala` is a critical component in the ScalablyTyped converter system that transforms TypeScript AST representations into Scala.js-compatible Scala AST. This phase bridges the gap between TypeScript definitions and Scala.js code, implementing numerous transformations to handle Scala.js limitations and ensure type safety.

## Purpose and Role

The primary function of `Phase2ToScalaJs` is to:

1. **Convert TypeScript AST to Scala AST**: Transform the parsed TypeScript definitions (`LibTs`) into Scala.js-compatible code structures (`LibScalaJs`)
2. **Implement Scala.js Limitations**: Apply transformations to ensure compatibility with Scala.js runtime constraints, particularly around method erasure and inheritance
3. **Apply Type System Adaptations**: Handle differences between TypeScript's structural typing and Scala's nominal typing system

As stated in the class documentation:
> "This phase starts by going from the typescript AST to the scala AST. Then the phase itself implements a bunch of scala.js limitations, like ensuring no methods erase to the same signature"

## Key Components

### Main Class Structure

```scala
class Phase2ToScalaJs(
    pedantic:                 Boolean,
    useDeprecatedModuleNames: Boolean,
    scalaVersion:             Versions.Scala,
    enableScalaJsDefined:     Selection[TsIdentLibrary],
    outputPkg:                Name,
    flavour:                  FlavourImpl,
) extends Phase[LibTsSource, LibTs, LibScalaJs]
```

**Configuration Parameters:**
- `pedantic`: Controls strict validation and error reporting
- `useDeprecatedModuleNames`: Backward compatibility flag for module naming
- `scalaVersion`: Target Scala version for compilation
- `enableScalaJsDefined`: Selection of libraries that should use `@ScalaJSDefined` annotation
- `outputPkg`: Target package name for generated Scala code
- `flavour`: Implementation strategy (Normal, Japgolly, etc.) that affects code generation

### Core Data Structures

#### Input: LibTs
- **Source**: `LibTsSource` - File system location and metadata
- **Parsed**: `TsParsedFile` - TypeScript AST representation
- **Dependencies**: Transitive dependency graph of other TypeScript libraries

#### Output: LibScalaJs
- **PackageTree**: Scala AST representation with classes, traits, objects
- **Dependencies**: Converted Scala.js dependencies
- **Names**: Adaptive naming strategy for identifier resolution
- **Metadata**: Library version, standard library flags, etc.

## Implementation Details

### Transformation Pipeline

The conversion process follows a carefully orchestrated sequence of transformations:

#### 1. Initial Setup and Dependency Resolution
```scala
val knownLibs = garbageCollectLibs(tsLibrary)
getDeps(knownLibs).map { scalaDeps =>
  val scalaName = ImportName(tsLibrary.name)
  val scope = new TreeScope.Root(...)
```

#### 2. Import Infrastructure Creation
- **ImportName**: Handles identifier conversion from TypeScript to Scala naming conventions
- **ImportType**: Manages type reference resolution and conversion
- **ImportTree**: Core converter that transforms TypeScript AST nodes to Scala AST

#### 3. Scala Transformation Chain
The `ScalaTransforms` pipeline applies 11 sequential transformations:

1. **CleanupTrivial + ModulesCombine**: Remove trivial type aliases and merge modules
2. **TypeRewriterCast**: Apply flavour-specific type conversions
3. **RemoveDuplicateInheritance + CleanIllegalNames + Deduplicator**: Clean up inheritance hierarchies
4. **FakeLiterals**: Convert TypeScript literal types to Scala equivalents
5. **UnionToInheritance**: Transform union types into inheritance hierarchies
6. **LimitUnionLength**: Prevent excessively complex union types
7. **RemoveMultipleInheritance**: Ensure single inheritance with trait mixins
8. **CombineOverloads**: Merge method overloads with compatible signatures
9. **FilterMemberOverrides**: Remove unnecessary override declarations
10. **InferMemberOverrides**: Add required override keywords
11. **CompleteClass**: Implement abstract members for `@ScalaJSDefined` classes

### Key Algorithms

#### Garbage Collection (`garbageCollectLibs`)
```scala
private def garbageCollectLibs(lib: LibTs): SortedSet[LibTsSource] = {
  val all: SortedSet[LibTsSource] = lib.transitiveDependencies.keys.to[SortedSet]
  val referenced: Set[TsIdentLibrary] = 
    TsTreeTraverse.collect(lib.parsed) { case x: ts.TsIdentLibrary => x }.toSet
  all.filter(x => referenced(x.libName))
}
```

This algorithm eliminates unused dependencies by:
1. Collecting all transitive dependencies
2. Finding actually referenced libraries in the TypeScript AST
3. Filtering to keep only referenced dependencies

#### Erasure and Method Signature Handling
The `Erasure` class handles method signature conflicts that arise from TypeScript's more flexible type system:
- Simplifies complex types to their erased forms
- Ensures no two methods have identical erased signatures
- Handles Scala 2 vs Scala 3 differences (e.g., `UNDEFINED` type handling)

## Input/Output Flow

### Input Processing
1. **LibTsSource**: File system representation with package.json, tsconfig.json
2. **LibTs**: Parsed TypeScript AST with resolved dependencies
3. **Dependencies**: Map of already-converted Scala.js libraries

### Output Generation
1. **Scala AST Creation**: `importTree(tsLibrary, logger)` converts TS → Scala
2. **Transformation Application**: Sequential application of all transforms
3. **LibScalaJs Assembly**: Final package with metadata and dependencies

### Pipeline Integration
```
Phase1ReadTypescript → Phase2ToScalaJs → PhaseFlavour → Phase3Compile
     (TS parsing)      (TS→Scala AST)    (Flavour)     (Compilation)
```

## Context in Conversion Process

### Relationship to Other Phases

**Phase1ReadTypescript** (Predecessor):
- Parses TypeScript files into AST
- Resolves module system and dependencies
- Applies TypeScript-specific transformations
- Outputs `LibTs` with cleaned TypeScript AST

**PhaseFlavour** (Successor):
- Applies flavour-specific transformations (React, Japgolly, etc.)
- Handles final code generation strategies
- Applies mangling and sorting

**Phase3Compile** (Final):
- Compiles Scala code to JVM bytecode
- Generates SBT project files
- Publishes to local repository

### Design Patterns

#### Visitor Pattern
Extensive use of tree transformation visitors:
```scala
S.CleanupTrivial.visitPackageTree(scope)
new S.RemoveDuplicateInheritance(parentResolver()).visitPackageTree(scope)
```

#### Strategy Pattern
`FlavourImpl` allows different code generation strategies:
- `NormalFlavour`: Standard Scala.js bindings
- `JapgollyFlavour`: React-specific optimizations

#### Builder Pattern
Progressive construction of the final `LibScalaJs` through transformation pipeline.

## Dependencies and Integration

### Key Dependencies
- **TreeScope**: Provides symbol resolution and scoping context
- **Erasure**: Handles method signature erasure for JVM compatibility
- **ParentsResolver**: Resolves inheritance hierarchies
- **CleanIllegalNames**: Ensures valid Scala identifiers

### Error Handling
Uses `PhaseRes[LibTsSource, LibScalaJs]` for composable error handling:
- `PhaseRes.Ok(value)`: Successful transformation
- `PhaseRes.Ignore()`: Skip processing (circular dependencies)
- `PhaseRes.Failure(errors)`: Transformation errors with context

This phase is essential for bridging the semantic gap between TypeScript's flexible type system and Scala.js's more constrained runtime environment, ensuring that generated code is both type-safe and performant.

## Detailed Transformation Analysis

### Critical Transformations Explained

#### CleanupTrivial + ModulesCombine
**Purpose**: Eliminates redundant type aliases and consolidates module definitions.

**Process**:
- Removes type aliases marked with `Marker.IsTrivial`
- Combines multiple module declarations with the same name
- Converts standalone functions to `apply` methods in companion objects
- Merges fields and methods into appropriate module structures

**Example Impact**:
```typescript
// TypeScript
declare module "lib" {
  function foo(): void;
  const bar: string;
}
declare module "lib" {
  function baz(): number;
}
```
Becomes a single Scala object with `apply` methods and namespace fields.

#### TypeRewriterCast (Flavour-Specific)
**Purpose**: Applies flavour-specific type conversions based on the target framework.

**Flavour Examples**:
- **NormalFlavour**: Standard Scala.js DOM types (`HTMLElement`, `Event`)
- **JapgollyFlavour**: React-specific types (`ReactElement`, `Component`)

#### RemoveDuplicateInheritance
**Purpose**: Resolves inheritance conflicts that are valid in TypeScript but problematic in Scala.

**Challenges Addressed**:
- TypeScript allows inheriting from multiple interfaces with conflicting members
- Scala requires unambiguous inheritance hierarchies
- Handles type parameter variance issues

#### FakeLiterals
**Purpose**: Converts TypeScript literal types to Scala equivalents.

**Transformations**:
- String literals → `String` with `@JSName` annotations
- Numeric literals → appropriate numeric types
- Boolean literals → `Boolean` type
- Creates phantom types for literal type safety

#### UnionToInheritance
**Purpose**: Transforms TypeScript union types into Scala inheritance hierarchies.

**Strategy**:
- Creates sealed trait hierarchies for union types
- Generates case classes/objects for union members
- Maintains type safety while enabling pattern matching

**Example**:
```typescript
type Status = "loading" | "success" | "error"
```
Becomes:
```scala
sealed trait Status extends js.Object
object Status {
  val loading: "loading" = "loading"
  val success: "success" = "success"
  val error: "error" = "error"
}
```

#### CombineOverloads
**Purpose**: Merges method overloads that have compatible erased signatures.

**Erasure Considerations**:
- Methods with identical erased signatures cannot coexist in JVM
- Combines overloads using union types or default parameters
- Preserves type safety while ensuring JVM compatibility

#### FilterMemberOverrides + InferMemberOverrides
**Purpose**: Manages override declarations for proper inheritance.

**FilterMemberOverrides**:
- Removes unnecessary override declarations
- Renames conflicting members to avoid signature clashes
- Optimizes for IDE performance

**InferMemberOverrides**:
- Adds required `override` keywords where needed
- Handles multiple inheritance scenarios
- Ensures proper method resolution

#### CompleteClass
**Purpose**: Implements abstract members for `@ScalaJSDefined` classes.

**Functionality**:
- Provides implementations for abstract methods from traits
- Forwards constructors from parent classes
- Handles TypeScript's flexible instantiation patterns

### Performance and Optimization Considerations

#### Caching Strategy
- `TreeScope` uses caching for symbol resolution
- `Erasure` and `ParentsResolver` instances are reused across transformations
- Dependency resolution is memoized

#### Memory Management
- Immutable data structures prevent accidental mutations
- Garbage collection of unused dependencies reduces memory footprint
- Streaming processing for large library sets

#### Compilation Efficiency
- Transformations are ordered to minimize re-processing
- Early filtering eliminates unnecessary work
- Parallel processing where dependencies allow

## Error Handling and Diagnostics

### Common Error Scenarios
1. **Circular Dependencies**: Detected and handled gracefully with `IsCircular` flag
2. **Naming Conflicts**: Resolved through `CleanIllegalNames` transformation
3. **Type Erasure Conflicts**: Handled by method signature analysis and renaming
4. **Invalid Inheritance**: Cleaned up by inheritance-related transformations

### Debugging Support
- Comprehensive logging through `Logger[Unit]` interface
- Phase-specific error context in `PhaseRes.Failure`
- Pedantic mode for strict validation and detailed error reporting

### Recovery Mechanisms
- Graceful degradation when transformations fail
- Fallback strategies for complex type scenarios
- Isolation of errors to prevent cascade failures

This comprehensive transformation pipeline ensures that the generated Scala.js code is not only syntactically correct but also semantically equivalent to the original TypeScript definitions while respecting Scala.js runtime constraints.
