# Phase1ReadTypescript.scala Documentation

## Overview

`Phase1ReadTypescript.scala` is a critical component of the ScalablyTyped converter that serves as the first phase in transforming TypeScript definition files into Scala.js bindings. This phase is responsible for parsing TypeScript files, implementing the module system, and performing extensive tree transformations to simplify complex TypeScript constructs into a more manageable intermediate representation.

**Primary Purpose**: Convert raw TypeScript definition files (`*.d.ts`) into a simplified, normalized TypeScript AST that can be further processed by subsequent phases.

**Role in System**: Acts as the entry point for TypeScript processing in the conversion pipeline, taking `LibTsSource` inputs and producing `LibTs` outputs with parsed and transformed TypeScript trees.

## Key Components

### Main Class: Phase1ReadTypescript

```scala
class Phase1ReadTypescript(
    resolve:                 LibraryResolver,
    calculateLibraryVersion: CalculateLibraryVersion,
    ignored:                 Set[TsIdentLibrary],
    ignoredModulePrefixes:   Set[List[String]],
    pedantic:                Boolean,
    parser:                  InFile => Either[String, TsParsedFile],
    expandTypeMappings:      Selection[TsIdentLibrary],
) extends Phase[LibTsSource, LibTsSource, LibTs]
```

**Constructor Parameters**:
- `resolve`: LibraryResolver for resolving module dependencies
- `calculateLibraryVersion`: Function to determine library versions
- `ignored`: Set of libraries to skip processing
- `ignoredModulePrefixes`: Module prefixes to ignore
- `pedantic`: Whether to use strict validation
- `parser`: Function to parse individual TypeScript files
- `expandTypeMappings`: Selection of libraries for type mapping expansion

### Core Data Structures

#### LibTsSource
Represents a TypeScript library source with metadata:
- `folder`: Source folder location
- `libName`: Library identifier
- `packageJsonOpt`: Optional package.json configuration
- `tsConfig`: Optional TypeScript configuration

#### LibTs
The output of Phase1, containing:
- `source`: Original LibTsSource
- `version`: Calculated library version
- `parsed`: Transformed TsParsedFile
- `dependencies`: Map of dependent libraries

#### TsParsedFile
Represents a parsed TypeScript file:
- `comments`: File-level comments
- `directives`: TypeScript directives (references, imports)
- `members`: Top-level declarations and containers
- `codePath`: Location information for the file

## Core Functionality

### 1. File Discovery and Inclusion

The phase determines which files to process based on the source type:

```scala
val includedFiles: IArray[InFile] = source match {
  case LibTsSource.StdLibSource(_, files, _) =>
    PathsFromTsLibSource.filesFrom(files.head.folder)
  case f @ LibTsSource.FromFolder(_, TsIdentLibrarySimple("typescript")) =>
    f.shortenedFiles  // Don't include std lib
  case f: LibTsSource.FromFolder =>
    val bound = f.shortenedFiles.headOption.map(_.folder).getOrElse(f.folder)
    PathsFromTsLibSource.filesFrom(bound)
}
```

### 2. Lazy File Processing

Files are processed lazily using a `SortedMap[InFile, Lazy[(TsParsedFile, Set[LibTsSource])]]` to handle circular dependencies and optimize memory usage.

### 3. Dependency Resolution

The phase resolves three types of dependencies:
- **Directive Dependencies**: From `/// <reference>` directives
- **Module Dependencies**: From `import`/`export` statements
- **Inferred Dependencies**: Automatically detected based on usage patterns

### 4. File Preprocessing Pipeline

Each file undergoes several preprocessing steps:
1. **Parsing**: Convert raw text to TsParsedFile AST
2. **Module Inference**: Determine module names and structure
3. **External Reference Resolution**: Resolve imports and references
4. **Dependency Collection**: Track all dependencies
5. **File Inlining**: Merge referenced non-module files
6. **Origin Marking**: Add source location information

## Dependencies

### External Libraries
- `com.olvind.logging`: Logging framework
- `org.scalablytyped.converter.Selection`: Library selection utilities
- `scala.collection.immutable.{SortedMap, SortedSet}`: Immutable collections

### Internal Dependencies
- `LibraryResolver`: Module and library resolution
- `TsTreeScope`: Type and term lookup facility
- `FlattenTrees`: Tree merging and flattening utilities
- `ResolveExternalReferences`: Module reference resolution
- Various transformation modules in `ts.transforms`

## Input/Output

### Input: LibTsSource
- **StdLibSource**: TypeScript standard library definitions
- **FromFolder**: User library from filesystem folder
- Contains metadata like package.json, file paths, library name

### Output: LibTs
- Parsed and transformed TypeScript AST
- Resolved dependency graph
- Version information
- Ready for next phase processing

## Code Flow

### Main Execution Path

1. **Input Validation**
   ```scala
   source match {
     case source if ignored(source.libName) || isCircular => PhaseRes.Ignore()
     case source => // Process the source
   }
   ```

2. **File Discovery**
   - Determine included files based on source type
   - Handle special cases (stdlib, typescript library)

3. **Lazy File Preparation**
   - Create lazy evaluation map for all files
   - Parse each file and resolve dependencies
   - Handle directives and module resolution

4. **Dependency Collection**
   ```scala
   val depsFromFiles = preparedFiles.foldLeft(Set.empty[LibTsSource]) { 
     case (acc, (_, deps)) => acc ++ deps 
   }
   ```

5. **Tree Flattening and Merging**
   - Combine all parsed files into single tree
   - Merge duplicate declarations
   - Handle module exports and proxies

6. **Transformation Pipeline**
   - Apply extensive series of transformations
   - Simplify TypeScript constructs
   - Normalize the AST structure

7. **Final Assembly**
   - Calculate library version
   - Create LibTs with all components
   - Return result with dependencies

### Transformation Pipeline

The transformation pipeline consists of 20+ sequential transformations:

```scala
List(
  T.LibrarySpecific(libName),                    // Library-specific fixes
  T.SetJsLocation,                               // Set JavaScript locations
  T.SimplifyParents >> T.RemoveStubs,            // Simplify inheritance
  T.InferTypeFromExpr >> T.InferEnumTypes,       // Type inference
  T.NormalizeFunctions >> T.MoveStatics,         // Function normalization
  modules.HandleCommonJsModules,                 // CommonJS handling
  T.QualifyReferences,                           // Qualify type references
  modules.AugmentModules,                        // Module augmentation
  T.ResolveTypeQueries,                          // Resolve typeof queries
  modules.ReplaceExports,                        // Export resolution
  ModuleAsGlobalNamespace,                       // Global namespace handling
  FlattenTrees,                                  // Tree flattening
  T.ExpandTypeMappings,                          // Type mapping expansion
  T.ExtractInterfaces,                           // Interface extraction
  T.ExtractClasses,                              // Class extraction
  // ... and more
)
```

## Important Implementation Details

### Circular Dependency Handling
The phase uses lazy evaluation and circular dependency detection to handle complex dependency graphs safely.

### Memory Optimization
- Lazy file processing prevents loading all files into memory
- Caching mechanisms in TsTreeScope for expensive lookups
- Efficient tree sharing and reuse

### Module System Implementation
- Supports both CommonJS and ES6 module systems
- Handles module augmentation and declaration merging
- Resolves complex export/import patterns

### React-Specific Handling
Special processing for React libraries due to their unique patterns:
```scala
val involvesReact = {
  val react = TsIdentLibrarySimple("react")
  source.libName === react || deps.exists { case (s, _) => s.libName === react }
}
```

### Error Handling and Logging
- Comprehensive logging at file and operation level
- Graceful handling of parse errors and missing dependencies
- Pedantic mode for strict validation

### Performance Considerations
- Sorted collections for deterministic processing
- Efficient tree traversal and transformation
- Minimal memory allocation during transformations

This phase represents the most complex part of the TypeScript-to-Scala conversion process, handling the full complexity of the TypeScript type system and module system while preparing a clean, normalized representation for subsequent processing phases.

## Detailed Transformation Pipeline Analysis

### Phase 1: Library-Specific Transformations
```scala
T.LibrarySpecific(libName).fold[TsParsedFile => TsParsedFile](identity)(_.visitTsParsedFile(scope))
```
Applies library-specific fixes and workarounds for known issues in popular TypeScript libraries.

### Phase 2: Location and Structure Setup
```scala
T.SetJsLocation.visitTsParsedFile(JsLocation.Global(TsQIdent.empty))
```
Sets JavaScript location information for proper code generation.

### Phase 3: Tree Simplification
```scala
T.SimplifyParents >> T.RemoveStubs >> T.InferTypeFromExpr >>
T.InferEnumTypes >> T.NormalizeFunctions >> T.MoveStatics
```
- **SimplifyParents**: Simplifies complex inheritance hierarchies
- **RemoveStubs**: Removes placeholder declarations
- **InferTypeFromExpr**: Infers types from expressions where possible
- **InferEnumTypes**: Determines enum value types
- **NormalizeFunctions**: Standardizes function declarations
- **MoveStatics**: Reorganizes static members

### Phase 4: Module System Handling
```scala
modules.HandleCommonJsModules >> modules.RewriteExportStarAs
```
Processes CommonJS modules and rewrites export patterns for compatibility.

### Phase 5: Reference Resolution
```scala
new T.QualifyReferences(skipValidation = false).visitTsParsedFile(scope)
```
Resolves and qualifies all type references to their full paths.

### Phase 6: Module Augmentation
```scala
modules.AugmentModules(scope.caching)
```
Handles TypeScript module augmentation declarations.

### Phase 7: Type Query Resolution
```scala
T.ResolveTypeQueries.visitTsParsedFile(scope.enableUnqualifiedLookup.caching)
```
Resolves `typeof` expressions and type queries.

### Phase 8: Export Processing
```scala
new modules.ReplaceExports(LoopDetector.initial).visitTsParsedFile(scope)
```
Processes and normalizes export declarations.

### Phase 9: Global Namespace Handling
```scala
file => ModuleAsGlobalNamespace(scope.root.libName, file)
modules.MoveGlobals.apply
```
Converts modules to global namespaces and reorganizes global declarations.

### Phase 10: Tree Flattening
```scala
FlattenTrees.apply
```
Merges duplicate declarations and flattens nested structures.

### Phase 11: Type System Enhancements
```scala
T.DefaultedTypeArguments >> T.TypeAliasIntersection >> T.RejiggerIntersections
```
- **DefaultedTypeArguments**: Adds default type arguments
- **TypeAliasIntersection**: Handles type alias intersections
- **RejiggerIntersections**: Optimizes intersection types

### Phase 12: Type Mapping Expansion (Conditional)
```scala
if (expandTypeMappings(libName)) T.ExpandTypeMappings.visitTsParsedFile(scope.caching)
if (expandTypeMappings(libName)) T.ExpandTypeMappings.After.visitTsParsedFile(scope.caching)
```
Expands type mappings for selected libraries to improve type safety.

### Phase 13: Advanced Type Processing
```scala
T.TypeAliasToConstEnum >> T.ForwardCtors >> T.ExpandTypeParams >>
T.UnionTypesFromKeyOf >> T.DropProperties >> T.InferReturnTypes >>
T.RewriteTypeThis >> T.InlineConstEnum >> T.InlineTrivial
```
- **TypeAliasToConstEnum**: Converts type aliases to const enums
- **ForwardCtors**: Forwards constructor signatures
- **ExpandTypeParams**: Expands type parameters
- **UnionTypesFromKeyOf**: Creates union types from keyof operations
- **DropProperties**: Removes unnecessary properties
- **InferReturnTypes**: Infers function return types
- **RewriteTypeThis**: Handles `this` type references
- **InlineConstEnum**: Inlines constant enum values
- **InlineTrivial**: Inlines trivial type aliases

### Phase 14: Type Lookup Resolution
```scala
T.ResolveTypeLookups.visitTsParsedFile(scope.caching)
```
Resolves complex type lookups and indexed access types.

### Phase 15: Type Alias Preferences
```scala
x => T.PreferTypeAlias(x, scope)
```
Determines when to prefer type aliases over interfaces.

### Phase 16: Interface Extraction
```scala
T.ExtractInterfaces(libName, TsIdent("anon"), scope.caching)
```
Extracts anonymous object types into named interfaces.

### Phase 17: Class Processing (React-Aware)
```scala
if (involvesReact) T.ExtractClasses
else T.ExtractClasses >> T.ExpandCallables
```
Extracts classes and handles callable types, with special React handling.

### Phase 18: Final Cleanup
```scala
T.SplitMethods >> T.RemoveDifficultInheritance >> T.VarToNamespace
```
- **SplitMethods**: Separates overloaded methods
- **RemoveDifficultInheritance**: Simplifies complex inheritance
- **VarToNamespace**: Converts variables to namespaces where appropriate

## Error Handling Strategies

### Parse Error Recovery
```scala
parser(file) match {
  case Left(msg) =>
    logger.withContext(file).fatal(s"Couldn't parse: $msg")
  case Right(parsed) => // Continue processing
}
```

### Dependency Resolution Failures
```scala
resolve.module(source, file.folder, value) match {
  case Some(ResolvedModule.NotLocal(depSource, _)) => deps += depSource
  case Some(ResolvedModule.Local(depSource, _)) =>
    logger.warn(s"unexpected typeref from local file $depSource")
  case _ =>
    logger.warn(s"directives: couldn't resolve $dir")
}
```

### Circular Reference Detection
The phase uses `LoopDetector` to identify and handle circular references safely:
```scala
new modules.ReplaceExports(LoopDetector.initial)
```

## Performance Optimizations

### Caching Strategy
- **TsTreeScope.caching**: Caches expensive scope lookups
- **Lazy evaluation**: Defers file processing until needed
- **Sorted collections**: Ensures deterministic processing order

### Memory Management
- **IArray usage**: Immutable arrays for better memory efficiency
- **Builder patterns**: Efficient collection building
- **Scope sharing**: Reuses scope objects across transformations

### Parallel Processing Potential
While currently sequential, the lazy evaluation structure allows for potential parallelization of independent file processing.

## Integration Points

### With Parser
```scala
parser: InFile => Either[String, TsParsedFile]
```
Integrates with the TypeScript parser to convert raw files to AST.

### With Library Resolver
```scala
resolve: LibraryResolver
```
Uses LibraryResolver for module and dependency resolution.

### With Next Phase
Produces `LibTs` objects that serve as input to subsequent conversion phases.

### With Logging System
Comprehensive integration with the logging system for debugging and monitoring:
```scala
logger.withContext(file).info(s"Preprocessing")
```

This documentation provides a comprehensive understanding of Phase1ReadTypescript.scala, covering its architecture, functionality, and implementation details necessary for maintenance and extension of the codebase.
