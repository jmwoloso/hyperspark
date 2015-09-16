package it.polimi.hyperh.algorithms

import it.polimi.hyperh.problem.Problem
import it.polimi.hyperh.solution.EvaluatedSolution
import scala.util.Random
import util.Timeout
import it.polimi.hyperh.solution.Solution

/**
 * @author Nemanja
 */
class HGAAlgorithm(
    p: Problem, 
    popSize: Int, 
    prob: Double, 
    coolingRate: Double,
    seed: Option[EvaluatedSolution]
    ) extends GAAlgorithm(popSize, seed) {
  /**
   * A secondary constructor.
   */
  def this(p: Problem, seedOption: Option[EvaluatedSolution]) {
    //popSize: 40, prob: 0.1, coolingRate: 0.95
    this(p, 40, 0.1, 0.95, seedOption)
  }
  def this(p: Problem) {
    //popSize: 40, prob: 0.1, coolingRate: 0.95
    this(p, 40, 0.1, 0.95, None)
  }
  var temperatureUB:Double = 2000.0 //dummy initialization

  
  def acceptanceProbability(delta: Int, temperature: Double): Double = {
    scala.math.min(1.0, scala.math.pow(2.71828, (-delta / temperature)))
  }
  override def evaluate(p: Problem): EvaluatedSolution = {
    val timeLimit = p.numOfMachines * (p.numOfJobs / 2.0) * 60 //termination is n*(m/2)*60 milliseconds
    evaluate(p, timeLimit)
  }
  override def evaluate(p:Problem, timeLimit: Double): EvaluatedSolution = {
    //INITIALIZE POPULATION
    var population = initSeedPlusRandom(p, popSize)
    var bestSolution = population.minBy(_.value)
    var worstSolution = population.maxBy(_.value)
    val delta = worstSolution.value - bestSolution.value
    temperatureUB = -delta / scala.math.log(prob)
    val expireTimeMillis = Timeout.setTimeout(timeLimit)
    
    while (Timeout.notTimeout(expireTimeMillis)) {
      //DIVIDE POPULATION IN 4 SUBPOPULATION
      val subPopSize = popSize / 4
      var subpopulation1 = population.take(subPopSize)
      var subpopulation2 = population.drop(subPopSize).take(subPopSize)
      var subpopulation3 = population.drop(2*subPopSize).take(subPopSize)
      var subpopulation4 = population.drop(3*subPopSize).take(subPopSize)
      //CROSSOVER
      subpopulation1 = crossover(p, subpopulation1, bestSolution, crossoverLOX, expireTimeMillis)
      subpopulation2 = crossover(p, subpopulation2, bestSolution, crossoverPMX, expireTimeMillis)
      subpopulation3 = crossover(p, subpopulation3, bestSolution, crossoverC1, expireTimeMillis)
      subpopulation4 = crossover(p, subpopulation4, bestSolution, crossoverNABEL, expireTimeMillis)
      //UPDATE POPULATION
      var metpopulation = subpopulation1 ++ subpopulation2 ++ subpopulation3 ++ subpopulation4
      val crossBest = metpopulation.minBy(_.value)
      if(crossBest.value < bestSolution.value)
        bestSolution = crossBest
      //METROPOLIS MUTATION
      metpopulation = metropolis(p, metpopulation, expireTimeMillis)
      val metBest = population.minBy(_.value)
      if(metBest.value < bestSolution.value)
        bestSolution = metBest
      
    } //endwhile
    //RETURN BEST SOLUTION
    bestSolution
  }
  def crossover(p: Problem, subpopulation: Array[EvaluatedSolution], bestSolution: EvaluatedSolution,
      operator: (List[Int], List[Int]) => (List[Int], List[Int]), expireTimeMillis: Double): Array[EvaluatedSolution] = {
    var newPopulation = subpopulation
    val Ps = subpopulation.size
    //parent1 uses bestSolution
    val parent1 = bestSolution
    //select parent2 using uniform distribution
    val parent2 = subpopulation(Random.nextInt(Ps))
    //apply crossover operator
    val children = operator(parent1.solution.toList, parent2.solution.toList)
    val child1 = Problem.evaluate(p, new Solution(children._1))
    val child2 = Problem.evaluate(p, new Solution(children._2))
    newPopulation = newPopulation ++ Array(child1) ++ Array(child2)
    newPopulation = newPopulation.sortBy[Int](_.value)(Ordering.Int).take(Ps)
    newPopulation
  }
  def metropolis(p: Problem, population: Array[EvaluatedSolution], expireTimeMillis: Double) = {

    var evOldPopulation = population
    var temperature = temperatureUB
    var iter = 0
    //repeat metropolis sample n times at current temperature
    while (iter < p.numOfJobs && Timeout.notTimeout(expireTimeMillis)) {
      for(i <- 0 until evOldPopulation.size) {
        var newSolution: List[Int] = List()
        var localBest = evOldPopulation(i)
        var runs = 0
        while((runs < p.numOfJobs) && Timeout.notTimeout(expireTimeMillis)) {
          //START METROPOLIS SAMPLE ITERATION
          //generate random neighbouring solution
          //i mod 4, 0,1: swap, 2: inv, 3: ins
          val op = i % 4
          if(op == 0 || op == 1)
            newSolution = mutationSWAP(evOldPopulation(i).solution.toList)
          else if(op == 2)
            newSolution = mutationINV(evOldPopulation(i).solution.toList)
          else
            newSolution = mutationINS(evOldPopulation(i).solution.toList)
          //calculate its cost
          val evNewSolution = Problem.evaluate(p, new Solution(newSolution))
            
          val delta = evNewSolution.value - evOldPopulation(i).value
          //calculate acceptance probability
          val ap = acceptanceProbability(delta, temperature)
          val randomNo = Random.nextDouble()
          
          if ((delta <= 0) || (randomNo <= ap)) {
            evOldPopulation(i) = evNewSolution
          }
          //if we found better solution in the neighbourhood, update localBest
          if(evNewSolution.value < localBest.value) {
            localBest = evNewSolution
          } 
          runs = runs + 1
          //END METROPOLIS SAMPLE ITERATION
        }
        evOldPopulation(i) = localBest
        temperature = coolingRate*temperature
      }
      iter = iter + 1
    }//end while
    evOldPopulation
  }
}