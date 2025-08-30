package org.scalablytyped.converter.internal.ts

import org.scalablytyped.converter.internal.ts.parser._
import utest._

object ParserTests extends TestSuite {
  def tests = Tests {
    test("parseString - simple interface") {
      val input = """interface User {
        |  id: number;
        |  name: string;
        |}""".stripMargin

      val result = parseString(input)
      assert(result.isRight)

      result match {
        case Right(parsedFile) =>
          assert(parsedFile != null)
          assert(parsedFile.members.nonEmpty)
          parsedFile
        case Left(error) =>
          throw new Exception(s"Expected successful parse but got error: $error")
      }
    }

    test("parseString - empty input") {
      val result = parseString("")
      assert(result.isRight)

      result match {
        case Right(parsedFile) =>
          assert(parsedFile != null)
          assert(parsedFile.members.isEmpty)
          parsedFile
        case Left(error) =>
          throw new Exception(s"Expected successful parse of empty input but got error: $error")
      }
    }

    test("parseString - invalid syntax") {
      val input = "interface User { invalid syntax"
      val result = parseString(input)
      assert(result.isLeft)

      result match {
        case Left(error) =>
          assert(error.contains("Parse error"))
          error
        case Right(_) =>
          throw new Exception("Expected parse error but parsing succeeded")
      }
    }

    test("parseFileContent - with filename") {
      val input = """declare function hello(): string;"""
      val result = parseFileContent("test.d.ts", input)
      assert(result.isRight)

      result match {
        case Right(parsedFile) =>
          assert(parsedFile != null)
          assert(parsedFile.members.nonEmpty)
          parsedFile
        case Left(error) =>
          throw new Exception(s"Expected successful parse but got error: $error")
      }
    }

    test("cleanedString - removes BOM") {
      val inputWithBOM = "\uFEFF" + "interface Test {}"
      val cleaned = cleanedString(inputWithBOM)
      assert(!cleaned.startsWith("\uFEFF"))
      assert(cleaned == "interface Test {}")
    }

    test("cleanedString - normalizes line endings") {
      val inputWithCRLF = "interface Test {\r\n  id: number;\r\n}"
      val cleaned = cleanedString(inputWithCRLF)
      assert(!cleaned.contains("\r\n"))
      assert(cleaned.contains("\n"))
      assert(cleaned == "interface Test {\n  id: number;\n}")
    }

    test("cleanedString - trims whitespace") {
      val inputWithWhitespace = "  \n  interface Test {}  \n  "
      val cleaned = cleanedString(inputWithWhitespace)
      assert(cleaned == "interface Test {}")
    }

    test("parseString - complex namespace") {
      val input = """declare namespace MyLib {
        |  interface Config {
        |    url: string;
        |    timeout?: number;
        |  }
        |
        |  function create(config: Config): void;
        |}""".stripMargin

      val result = parseString(input)
      assert(result.isRight)

      result match {
        case Right(parsedFile) =>
          assert(parsedFile != null)
          assert(parsedFile.members.nonEmpty)
          // Verify we have a namespace declaration
          val hasNamespace = parsedFile.members.exists {
            case _: TsDeclNamespace => true
            case _                  => false
          }
          assert(hasNamespace)
          parsedFile
        case Left(error) =>
          throw new Exception(s"Expected successful parse but got error: $error")
      }
    }

    // Additional comprehensive parser tests
    test("parseString - type alias") {
      val input = "type StringOrNumber = string | number;"
      val result = parseString(input)
      assert(result.isRight)

      result match {
        case Right(parsedFile) =>
          assert(parsedFile.members.length == 1)
          val typeAlias = parsedFile.members.head.asInstanceOf[TsDeclTypeAlias]
          assert(typeAlias.name.value == "StringOrNumber")
          parsedFile
        case Left(error) =>
          throw new Exception(s"Expected successful parse but got error: $error")
      }
    }

    test("parseString - enum declaration") {
      val input = """enum Color {
        |  Red = "red",
        |  Green = "green",
        |  Blue = "blue"
        |}""".stripMargin

      val result = parseString(input)
      assert(result.isRight)

      result match {
        case Right(parsedFile) =>
          assert(parsedFile.members.length == 1)
          val enumDecl = parsedFile.members.head.asInstanceOf[TsDeclEnum]
          assert(enumDecl.name.value == "Color")
          assert(enumDecl.members.length == 3)
          parsedFile
        case Left(error) =>
          throw new Exception(s"Expected successful parse but got error: $error")
      }
    }

    test("parseString - class declaration") {
      val input = """class Animal {
        |  name: string;
        |  constructor(name: string);
        |  speak(): void;
        |}""".stripMargin

      val result = parseString(input)
      assert(result.isRight)

      result match {
        case Right(parsedFile) =>
          assert(parsedFile.members.length == 1)
          val classDecl = parsedFile.members.head.asInstanceOf[TsDeclClass]
          assert(classDecl.name.value == "Animal")
          assert(classDecl.members.length == 3)
          parsedFile
        case Left(error) =>
          throw new Exception(s"Expected successful parse but got error: $error")
      }
    }

    test("parseString - variable declaration") {
      val input = "declare const API_URL: string;"
      val result = parseString(input)
      assert(result.isRight)

      result match {
        case Right(parsedFile) =>
          assert(parsedFile.members.length == 1)
          val varDecl = parsedFile.members.head.asInstanceOf[TsDeclVar]
          assert(varDecl.name.value == "API_URL")
          assert(varDecl.readOnly)
          parsedFile
        case Left(error) =>
          throw new Exception(s"Expected successful parse but got error: $error")
      }
    }

    test("parseString - module declaration") {
      val input = """declare module "my-module" {
        |  export function helper(): void;
        |}""".stripMargin

      val result = parseString(input)
      assert(result.isRight)

      result match {
        case Right(parsedFile) =>
          assert(parsedFile.members.length == 1)
          val moduleDecl = parsedFile.members.head.asInstanceOf[TsDeclModule]
          assert(moduleDecl.name.value == "my-module")
          parsedFile
        case Left(error) =>
          throw new Exception(s"Expected successful parse but got error: $error")
      }
    }
  }
}
