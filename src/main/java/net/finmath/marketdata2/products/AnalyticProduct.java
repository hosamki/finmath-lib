/*
 * (c) Copyright Christian P. Fries, Germany. Contact: email@christian-fries.de.
 *
 * Created on 26.11.2012
 */
package net.finmath.marketdata2.products;

import net.finmath.marketdata2.model.AnalyticModel;
import net.finmath.modelling.Product;
import net.finmath.stochastic.RandomVariable;

/**
 * The interface which has to be implemented by a product which may
 * be evaluated using an <code>AnalyticModelFromCuvesAndVols</code>.
 *
 * @author Christian Fries
 * @version 1.0
 */
public interface AnalyticProduct extends Product {

	/**
	 * Return the valuation of the product using the given model.
	 * The model has to implement the modes of <code>AnalyticModel</code>.
	 *
	 * @param evaluationTime The evaluation time as double. Cash flows prior and including this time are not considered.
	 * @param model The model under which the product is valued.
	 * @return The value of the product using the given model.
	 */
	RandomVariable getValue(double evaluationTime, AnalyticModel model);
}
