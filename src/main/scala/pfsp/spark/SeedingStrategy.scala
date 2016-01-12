package pfsp.spark

import it.polimi.hyperh.solution.Solution
import it.polimi.hyperh.spark.SeedingStrategy
import pfsp.solution.PfsSolution
import scala.util.Random
/**
 * @author Nemanja
 */
class SlidingWindow(windowSize: Int) extends SeedingStrategy {
  override def divide(seedOption: Option[Solution], N: Int): Array[Option[Solution]] = {
    val seed = seedOption.getOrElse(throw new RuntimeException("SeedingStrategySlidingWindow: None value for Option[Solution]"))
    val perm = seed.asInstanceOf[PfsSolution].permutation
    if(N+windowSize > perm.size)
      throw new RuntimeException("SeedingStrategySlidingWindow: can't slide that much. Reason: N+windowSize  > solution.permutation.size. Try to decrease the windowSize parameter")
    var array: Array[Option[Solution]] = Array()
    for (i <- 0 until N) {
      val window = perm.drop(i).take(windowSize)
      val allowed = perm.filterNot(window.toSet)
      val arrayTake = Random.shuffle(allowed.toList).toArray
      val leftPart = arrayTake.take(i)
      val rightPart = arrayTake.drop(i)
      val newSol = leftPart ++ window ++ rightPart
      array :+= Some(PfsSolution(newSol))
    }
    array
  }
  override def usesTheSeed(): Boolean = true
}
class SeedPlusSlidingWindow(windowSize: Int) extends SeedingStrategy {
  override def divide(seed: Option[Solution], N: Int): Array[Option[Solution]] = {
    seed +: new SlidingWindow(windowSize).divide(seed, N-1)
  }
  override def usesTheSeed(): Boolean = true
}