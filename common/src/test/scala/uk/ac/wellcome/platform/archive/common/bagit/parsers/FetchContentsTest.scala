package uk.ac.wellcome.platform.archive.common.bagit.parsers

import java.net.URI

import org.scalatest.{FunSpec, Matchers}

class FetchContentsTest extends FunSpec with Matchers {
  it("writes the lines of a fetch.txt") {
    val entries = Seq(
      FetchEntry(url = new URI("http://example.org/"), length = Some(25), filepath = "example.txt"),
      FetchEntry(url = new URI("https://wellcome.ac.uk/"), length = None, filepath = "logo.png")
    )

    val expected =
      s"""
         |http://example.org/ 25 example.txt
         |https://wellcome.ac.uk/ - logo.png
       """.stripMargin.trim

    FetchContents.write(entries) shouldBe expected
  }

  it("percent-encodes a CR, LF or CRLF in the filename") {
    val entries = Seq(
      "example\rnumber\r1.txt",
      "example\nnumber\n2.txt",
      "example\r\nnumber\r\n3.txt").map { filepath =>
      FetchEntry(url = new URI("http://example.org/"), length = None, filepath = filepath)
    }


      Seq(
      FetchEntry(url = new URI("http://example.org/"), length = None, filepath = "example.txt"),
      FetchEntry(url = new URI("https://wellcome.ac.uk/"), length = None, filepath = "logo.png")
    )

    val expected =
      s"""
         |http://example.org/ - example%0Dnumber%0D1.txt
         |http://example.org/ - example%0Anumber%0A2.txt
         |http://example.org/ - example%0D%0Anumber%0D%0A3.txt
       """.stripMargin.trim

    FetchContents.write(entries) shouldBe expected
  }
}
