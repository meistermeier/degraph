package de.schauderhaft.degraph.configuration

import scala.io.Source
import org.rogach.scallop.exceptions.ScallopException
import Slicer.toSlicer
import de.schauderhaft.degraph.analysis.dependencyFinder.AnalyzerLike
import de.schauderhaft.degraph.slicer.CombinedSlicer
import de.schauderhaft.degraph.slicer.ParallelCategorizer
import de.schauderhaft.degraph.analysis.dependencyFinder.IncludeExcludeFilter
import de.schauderhaft.degraph.slicer.InternalClassCategorizer
import de.schauderhaft.degraph.slicer.PackageCategorizer
import de.schauderhaft.degraph.slicer.MultiCategorizer.combine
import de.schauderhaft.degraph.model.Node
import de.schauderhaft.degraph.slicer.PatternMatchingFilter
import de.schauderhaft.degraph.graph.Graph
import de.schauderhaft.degraph.model.SimpleNode

/**
 * companion object allowing easy creation of a configuration from commandline arguments.
 */
object Configuration {

    /**
     * just pass the commandline arguments to this method to get an Either representing the
     * result of attempting to create a configuration out of the arguments.
     *
     * If something went wrong a Left will get returned containing an error message, including
     * some usage advice suitable for presenting it to the user.
     *
     * Otherwise a Right instance containing the complete configuration is returned.
     */
    def apply(args: Array[String]): Either[String, Configuration] = {
        val eitherConfig = fromCommandLine(args)

        eitherConfig
    }
    private def fromCommandLine(args: Array[String]): Either[String, Configuration] = {
        var errorMessage: Option[String] = None
        val commandLine = CommandLineParser.parse(args)
        commandLine.initialize { case ScallopException(m) => errorMessage = Some(m + "\nUsage:\n" + commandLine.builder.help) }
        errorMessage match {
            case Some(m) => Left(m)
            case _ if (commandLine.file.isEmpty) => Right((Configuration(
                classpath = Some(commandLine.classpath()),
                includes = commandLine.includeFilter(),
                excludes = commandLine.excludeFilter(),
                output = Some(commandLine.output()),
                categories = Map())))
            case _ => Right(new ConfigurationParser().parse(Source.fromFile(commandLine.file()).mkString))
        }
    }
}

/**
 * represents all the information configurable in commandline arguments and configuration files for Degraph.
 */
case class Configuration(
    classpath: Option[String] = None,
    includes: Seq[String] = Seq(),
    excludes: Seq[String] = Seq(),
    categories: Map[String, Seq[Pattern]] = Map(),
    output: Option[String] = None,
    constraint: Set[Constraint] = Set(CycleFree),
    analyzer: AnalyzerLike = null) {

    lazy val slicing = buildCategorizer(categories)

    def createGraph() =
        analyzer.analyze(classpath.get, slicing, buildFilter(includes, excludes))

    def valid = classpath.isDefined && output.isDefined

    def including(s: String): Configuration = copy(includes = includes :+ s)

    def forType(sliceType: String) = new ConstraintBuilder(this, sliceType)

    private[this] def buildFilter(includes: Seq[String],
        excludes: Seq[String]) = {
        new IncludeExcludeFilter(
            includes.map((x: String) => new PatternMatchingFilter(x)).toSet,
            excludes.map((x: String) => new PatternMatchingFilter(x)).toSet)
    }

    private[this] def buildCategorizer(categories: Map[String, Seq[Pattern]]): (AnyRef => Node) = {
        val slicers = for { (level, patterns) <- categories }
            yield buildCategorizer(level, patterns)
        val slicersWithPackages = new ParallelCategorizer(PackageCategorizer +: slicers.toSeq: _*)
        combine(InternalClassCategorizer, slicersWithPackages)
    }

    private[this] def buildCategorizer(slicing: String, groupings: Seq[Pattern]): (AnyRef => Node) =
        new CombinedSlicer(groupings.map(toSlicer(slicing, _)): _*)
}

class ConstraintBuilder(configuration: Configuration, sliceType: String) {
    private def any2Layer(arg: AnyRef): Layer = arg match {
        case s: String => LenientLayer(s)
        case l: Layer => l
        case _ => throw new IllegalArgumentException("Only arguments of type String or Layer are accepted")
    }

    private def modifyConfig(slices: IndexedSeq[AnyRef], toConstraint: (String, IndexedSeq[Layer]) => Constraint): Configuration =
        configuration.copy(
            constraint =
                configuration.constraint +
                    toConstraint(sliceType, slices.map((x: AnyRef) => any2Layer(x))))

    def allow(slices: AnyRef*): Configuration =
        modifyConfig(slices.toIndexedSeq, LayeringConstraint)

    def allowDirect(slices: AnyRef*): Configuration =
        modifyConfig(slices.toIndexedSeq, DirectLayeringConstraint)

}

sealed trait Pattern {
    def pattern: String
}
case class UnnamedPattern(val pattern: String) extends Pattern
case class NamedPattern(val pattern: String, name: String) extends Pattern

