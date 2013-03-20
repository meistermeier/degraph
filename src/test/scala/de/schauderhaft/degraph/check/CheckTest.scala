package de.schauderhaft.degraph.check

import org.junit.runner.RunWith
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import org.scalatest.OptionValues._
import de.schauderhaft.degraph.configuration.Configuration
import de.schauderhaft.degraph.graph.Graph
import de.schauderhaft.degraph.analysis.dependencyFinder.AnalyzerLike
import de.schauderhaft.degraph.model.SimpleNode
import de.schauderhaft.degraph.model.Node

@RunWith(classOf[JUnitRunner])
class CheckTest extends FunSuite with ShouldMatchers {
    val mod = "mod"

    test("configuration cotains the classpath") {
        Check.classpath.classpath.value should include("log4j")
    }

    //        forType("module").allow("a", "b", any("x","y","z"), none("u","v","w",)"c", )
    //        allowDirectOnly

    private def mockConfig(conns: Traversable[(Node, Node)]) = {
        val graph = new Graph()

        for ((a, b) <- conns) graph.connect(a, b)

        val analyzer = new AnalyzerLike {
            def analyze(
                sourceFolder: String,
                categorizer: Node => Node,
                filter: Node => Boolean): Graph = graph
        }

        new Configuration(
            classpath = Some("x"),
            analyzer = analyzer)
    }

    val ascending = for {
        x <- 'a' to 'c'
        y <- 'a' to 'c'
        if (x <= y)
    } yield (SimpleNode(mod, x.toString), SimpleNode(mod, y.toString))

    test("matcher accepts violation free graph for simple layering") {
        val conf = mockConfig(ascending).forType(mod).allow("a", "b", "c")
        Check.violationFree(conf).matches should be(true)
    }

}
