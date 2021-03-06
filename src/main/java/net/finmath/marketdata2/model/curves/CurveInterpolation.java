/*
 * (c) Copyright Christian P. Fries, Germany. Contact: email@christian-fries.de.
 *
 * Created on 20.05.2005
 */
package net.finmath.marketdata2.model.curves;

import java.io.IOException;
import java.io.Serializable;
import java.lang.ref.SoftReference;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.finmath.marketdata2.interpolation.RationalFunctionInterpolation;
import net.finmath.marketdata2.model.AnalyticModel;
import net.finmath.montecarlo.RandomVariableFromDoubleArray;
import net.finmath.stochastic.RandomVariable;
import net.finmath.time.FloatingpointDate;

/**
 * This class represents a curveFromInterpolationPoints build from a set of points in 2D.
 *
 * It provides different interpolation and extrapolation methods applied to a transformation of the input point,
 * examples are
 * <ul>
 * 	<li>linear interpolation of the input points</li>
 *  <li>linear interpolation of the log of the input points</li>
 *  <li>linear interpolation of the log of the input points divided by their respective time</li>
 * 	<li>cubic spline interpolation of the input points (or a function of the input points) (the curveFromInterpolationPoints will be C<sup>1</sup>).</li>
 * 	<li>Akima interpolation of the input points (or a function of the input points).</li>
 *  <li>etc.</li>
 * </ul>
 *
 * <br>
 *
 * For the interpolation methods provided see {@link net.finmath.marketdata.model.curves.CurveInterpolation.InterpolationMethod}.
 * For the extrapolation methods provided see {@link net.finmath.marketdata.model.curves.CurveInterpolation.ExtrapolationMethod}.
 * For the possible interpolation entities see {@link net.finmath.marketdata.model.curves.CurveInterpolation.InterpolationEntity}.
 *
 * To construct the curveFromInterpolationPoints, please use the inner class CurveBuilder (a builder pattern).
 *
 * For a demo on how to construct and/or calibrate a curveFromInterpolationPoints see, e.g.
 * net.finmath.tests.marketdata.curves.CurveTest.
 *
 * @author Christian Fries
 * @version 1.0
 */
public class CurveInterpolation extends AbstractCurve implements Serializable, Cloneable {

	/**
	 * Possible interpolation methods.
	 *
	 * @author Christian Fries
	 */
	public enum InterpolationMethod {
		/** Constant interpolation. Synonym of PIECEWISE_CONSTANT_LEFTPOINT. **/
		PIECEWISE_CONSTANT,
		/** Constant interpolation. Right continuous, i.e. using the value of the left end point of the interval. **/
		PIECEWISE_CONSTANT_LEFTPOINT,
		/** Constant interpolation using the value of the right end point of the interval. **/
		PIECEWISE_CONSTANT_RIGHTPOINT,
		/** Linear interpolation. **/
		LINEAR,
		//	/** Cubic spline interpolation. **/
		//	CUBIC_SPLINE,
		//	/** Akima interpolation (C1 sub-spline interpolation). **/
		//	AKIMA,
		//	/** Akima interpolation (C1 sub-spline interpolation) with a smoothing in the weights. **/
		//	AKIMA_CONTINUOUS,
		/** Harmonic spline interpolation (C1 sub-spline interpolation). **/
		HARMONIC_SPLINE,
		/** Harmonic spline interpolation (C1 sub-spline interpolation) with a monotonic filtering at the boundary points. **/
		HARMONIC_SPLINE_WITH_MONOTONIC_FILTERING

	}

	/**
	 * Possible extrapolation methods.
	 *
	 * @author Christian Fries
	 */
	public enum ExtrapolationMethod {
		/** Extrapolation using the interpolation function of the adjacent interval **/
		DEFAULT,
		/** Constant extrapolation. **/
		CONSTANT,
		/** Linear extrapolation. **/
		LINEAR
	}

	/**
	 * Possible interpolation entities.
	 * @author Christian Fries
	 */
	public enum InterpolationEntity {
		/** Interpolation is performed on the native point values, i.e. value(t) **/
		VALUE,
		/** Interpolation is performed on the log of the point values, i.e. log(value(t)) **/
		LOG_OF_VALUE,
		/** Interpolation is performed on the log of the point values divided by their respective time, i.e. log(value(t))/t **/
		LOG_OF_VALUE_PER_TIME
	}

	private static class Point implements Comparable<Point>, Serializable {
		private static final long serialVersionUID = 8857387999991917430L;

		private double time;
		private RandomVariable value;
		private boolean isParameter;

		/**
		 * @param time The time (or x-value) of the point.
		 * @param value The value (or y-value) of the point.
		 * @param isParameter A boolean specifying if this point is considered a "degree of freedom", e.g., in a calibration.
		 */
		Point(double time, RandomVariable value, boolean isParameter) {
			super();
			this.time = time;
			this.value = value;
			this.isParameter = isParameter;
		}

		@Override
		public int compareTo(Point point) {
			// Ordering of the curveFromInterpolationPoints points with respect to time.
			if(time < point.time) {
				return -1;
			}
			if(time > point.time) {
				return +1;
			}

			return 0;
		}

		@Override
		public Object clone() {
			return new Point(time,value,isParameter);
		}
	}

	/**
	 * A builder (following the builder pattern) for CurveFromInterpolationPoints objects.
	 * Allows to successively construct a curveFromInterpolationPoints object by adding points.
	 *
	 * @author Christian Fries
	 */
	public static class Builder implements CurveBuilder {
		private CurveInterpolation curveInterpolation = null;

		/**
		 * Build a curveFromInterpolationPoints.
		 */
		public Builder() {
			curveInterpolation = new CurveInterpolation(null, null);
		}

		/**
		 * Build a curveFromInterpolationPoints with a given name and given reference date.
		 *
		 * @param name The name of this curveFromInterpolationPoints.
		 * @param referenceDate The reference date for this curveFromInterpolationPoints, i.e., the date which defined t=0.
		 */
		public Builder(String name, LocalDate referenceDate) {
			curveInterpolation = new CurveInterpolation(name, referenceDate);
		}

		/**
		 * Build a curveFromInterpolationPoints by cloning a given curveFromInterpolationPoints.
		 *
		 * @param curveInterpolation A curveFromInterpolationPoints to be used as starting point for the new curveFromInterpolationPoints.
		 * @throws CloneNotSupportedException Thrown, when the curveFromInterpolationPoints could not be cloned.
		 */
		public Builder(CurveInterpolation curveInterpolation) throws CloneNotSupportedException {
			this.curveInterpolation = curveInterpolation.clone();
		}

		/* (non-Javadoc)
		 * @see net.finmath.marketdata.model.curves.CurveBuilderInterface#build()
		 */
		@Override
		public Curve build() {
			CurveInterpolation buildCurve = curveInterpolation;
			curveInterpolation = null;
			return buildCurve;
		}

		/**
		 * Set the interpolation method of the curveFromInterpolationPoints.
		 *
		 * @param interpolationMethod The interpolation method of the curveFromInterpolationPoints.
		 * @return A self reference to this curveFromInterpolationPoints build object.
		 */
		public CurveBuilder setInterpolationMethod(InterpolationMethod interpolationMethod) {
			curveInterpolation.interpolationMethod = interpolationMethod;
			return this;
		}

		/**
		 * Set the extrapolation method of the curveFromInterpolationPoints.
		 *
		 * @param extrapolationMethod The extrapolation method of the curveFromInterpolationPoints.
		 * @return A self reference to this curveFromInterpolationPoints build object.
		 */
		public CurveBuilder setExtrapolationMethod(ExtrapolationMethod extrapolationMethod) {
			curveInterpolation.extrapolationMethod = extrapolationMethod;
			return this;
		}

		/**
		 * Set the interpolationEntity of the curveFromInterpolationPoints.
		 *
		 * @param interpolationEntity The interpolation entity of the curveFromInterpolationPoints.
		 * @return A self reference to this curveFromInterpolationPoints build object.
		 */
		public CurveBuilder setInterpolationEntity(InterpolationEntity interpolationEntity) {
			curveInterpolation.interpolationEntity = interpolationEntity;
			return this;
		}

		/* (non-Javadoc)
		 * @see net.finmath.marketdata.model.curves.CurveBuilderInterface#addPoint(double, double, boolean)
		 */
		@Override
		public CurveBuilder addPoint(double time, RandomVariable value, boolean isParameter) {
			curveInterpolation.addPoint(time, value, isParameter);
			return this;
		}
	}

	private ArrayList<Point>	points					= new ArrayList<>();
	private ArrayList<Point>	pointsBeingParameters	= new ArrayList<>();
	private InterpolationMethod	interpolationMethod	= InterpolationMethod.LINEAR; //CUBIC_SPLINE;
	private ExtrapolationMethod	extrapolationMethod = ExtrapolationMethod.CONSTANT;
	private InterpolationEntity interpolationEntity = InterpolationEntity.LOG_OF_VALUE;

	private RationalFunctionInterpolation	rationalFunctionInterpolation =  null;
	private transient Object					rationalFunctionInterpolationLazyInitLock = new Object();
	private SoftReference<Map<Double, RandomVariable>> curveCacheReference = null;
	//private LIBORModelMonteCarloSimulationModel model;


	private static final long serialVersionUID = -4126228588123963885L;
	private static NumberFormat	formatterReal = NumberFormat.getInstance(Locale.US);


	/**
	 * Create a curveFromInterpolationPoints with a given name, reference date and an interpolation method from given points
	 *
	 * @param name The name of this curveFromInterpolationPoints.
	 * @param referenceDate The reference date for this curveFromInterpolationPoints, i.e., the date which defined t=0.
	 * @param interpolationMethod The interpolation method used for the curveFromInterpolationPoints.
	 * @param extrapolationMethod The extrapolation method used for the curveFromInterpolationPoints.
	 * @param interpolationEntity The entity interpolated/extrapolated.
	 * @param times A vector of times.
	 * @param values A vector of corresponding values.
	 */
	public CurveInterpolation(String name,
			LocalDate referenceDate,
			InterpolationMethod interpolationMethod,
			ExtrapolationMethod extrapolationMethod,
			InterpolationEntity interpolationEntity,
			double[] times,
			RandomVariable[] values) {
		super(name, referenceDate);
		this.interpolationMethod	= interpolationMethod;
		this.extrapolationMethod	= extrapolationMethod;
		this.interpolationEntity	= interpolationEntity;
		if(times.length != values.length) {
			throw new IllegalArgumentException("Length of times not equal to length of values.");
		}
		for(int i=0; i<times.length; i++) {
			this.addPoint(times[i], values[i], false);
		}
	}

	/**
	 * Create a curveFromInterpolationPoints with a given name, reference date and an interpolation method.
	 *
	 * @param name The name of this curveFromInterpolationPoints.
	 * @param referenceDate The reference date for this curveFromInterpolationPoints, i.e., the date which defined t=0.
	 * @param interpolationMethod The interpolation method used for the curveFromInterpolationPoints.
	 * @param extrapolationMethod The extrapolation method used for the curveFromInterpolationPoints.
	 * @param interpolationEntity The entity interpolated/extrapolated.
	 */

	protected CurveInterpolation(String name,
			LocalDate referenceDate,
			InterpolationMethod interpolationMethod,
			ExtrapolationMethod extrapolationMethod,
			InterpolationEntity interpolationEntity) {

		super(name, referenceDate);
		this.interpolationMethod	= interpolationMethod;
		this.extrapolationMethod	= extrapolationMethod;
		this.interpolationEntity	= interpolationEntity;

	}

	/**
	 * Create a curveFromInterpolationPoints with a given name, reference date.
	 *
	 * @param name The name of this curveFromInterpolationPoints.
	 * @param referenceDate The reference date for this curveFromInterpolationPoints, i.e., the date which defined t=0.
	 */
	private CurveInterpolation(String name, LocalDate referenceDate) {
		super(name, referenceDate);
	}


	@Override
	public RandomVariable getValue(double time)
	{
		return getValue(null, time);
	}

	@Override
	public RandomVariable getValue(AnalyticModel model, double time)
	{
		Map<Double, RandomVariable> curveCache = curveCacheReference != null ? curveCacheReference.get() : null;
		if(curveCache == null) {
			curveCache = new ConcurrentHashMap<>();
			curveCacheReference = new SoftReference<>(curveCache);
		}
		RandomVariable valueFromCache = curveCache.get(time);
		if(valueFromCache != null) {
			return valueFromCache;
		}

		RandomVariable value = valueFromInterpolationEntity(getInterpolationEntityValue(time), time);
		curveCache.put(time, value);
		return value;
	}

	private  RandomVariable getInterpolationEntityValue(double time)
	{
		synchronized(rationalFunctionInterpolationLazyInitLock) {
			// Lazy initialization of interpolation function
			if(rationalFunctionInterpolation == null) {
				double[] pointsArray = new double[points.size()];//time
				RandomVariable[] valuesArray = new RandomVariable[points.size()];
				for(int i=0; i<points.size(); i++) {
					pointsArray[i] = points.get(i).time;
					valuesArray[i] = points.get(i).value;
				}
				rationalFunctionInterpolation = new RationalFunctionInterpolation(
						pointsArray,
						valuesArray,
						RationalFunctionInterpolation.InterpolationMethod.valueOf(interpolationMethod.toString()),
						RationalFunctionInterpolation.ExtrapolationMethod.valueOf(extrapolationMethod.toString())
						);
			}
		}
		return rationalFunctionInterpolation.getValue(time);
	}


	/**
	 * Add a point to this curveFromInterpolationPoints. The method will throw an exception if the point
	 * is already part of the curveFromInterpolationPoints.
	 *
	 * @param time The x<sub>i</sub> in <sub>i</sub> = f(x<sub>i</sub>).
	 * @param value The y<sub>i</sub> in <sub>i</sub> = f(x<sub>i</sub>).
	 * @param isParameter If true, then this point is served via {@link #getParameter()} and changed via {@link #getCloneForParameter(RandomVariable[])}, i.e., it can be calibrated.
	 */
	protected void addPoint(double time, RandomVariable value, boolean isParameter) {
		synchronized (rationalFunctionInterpolationLazyInitLock) {
			if(interpolationEntity == InterpolationEntity.LOG_OF_VALUE_PER_TIME && time == 0) {
				boolean containsOne = false; int index=0;
				for(int i = 0; i< value.size(); i++){if(value.get(i)==1.0) {containsOne = true; index=i; break;}}
				if(containsOne && isParameter == false) {
					return;
				} else {
					throw new IllegalArgumentException("The interpolation method LOG_OF_VALUE_PER_TIME does not allow to add a value at time = 0 other than 1.0 (received 1 at index" + index + ").");
				}
			}

			RandomVariable interpolationEntityValue = interpolationEntityFromValue(value, time);

			int index = getTimeIndex(time);
			if(index >= 0) {
				if(points.get(index).value == interpolationEntityValue) {
					return;			// Already in list
				} else if(isParameter) {
					return;
				} else {
					throw new RuntimeException("Trying to add a value for a time for which another value already exists.");
				}
			}
			else {
				// Insert the new point, retain ordering.
				Point point = new Point(time, interpolationEntityValue, isParameter);
				points.add(-index-1, point);

				if(isParameter) {
					// Add this point also to the list of parameters
					int parameterIndex = getParameterIndex(time);
					if(parameterIndex >= 0) {
						new RuntimeException("CurveFromInterpolationPoints inconsistent.");
					}
					pointsBeingParameters.add(-parameterIndex-1, point);
				}
			}
			rationalFunctionInterpolation = null;
			curveCacheReference = null;
		}
	}

	/**
	 * Returns the interpolation method used by this curveFromInterpolationPoints.
	 *
	 * @return The interpolation method used by this curveFromInterpolationPoints.
	 */
	public InterpolationMethod getInterpolationMethod() {
		return interpolationMethod;
	}

	/**
	 * Returns the extrapolation method used by this curveFromInterpolationPoints.
	 *
	 * @return The extrapolation method used by this curveFromInterpolationPoints.
	 */
	public ExtrapolationMethod getExtrapolationMethod() {
		return extrapolationMethod;
	}

	/**
	 * Returns the interpolation entity used by this curveFromInterpolationPoints.
	 *
	 * @return The interpolation entity used by this curveFromInterpolationPoints.
	 */
	public InterpolationEntity getInterpolationEntity() {
		return interpolationEntity;
	}

	protected int getTimeIndex(double time) {
		Point point = new Point(time, new RandomVariableFromDoubleArray(Double.NaN), false);
		int index =  java.util.Collections.binarySearch(points, point);
		return index;
	}

	protected int getParameterIndex(double time) {
		Point point = new Point(time, new RandomVariableFromDoubleArray(Double.NaN), false);
		return java.util.Collections.binarySearch(pointsBeingParameters, point);
	}


	@Override
	public  RandomVariable[] getParameter() {
		RandomVariable[] parameters = new RandomVariable[pointsBeingParameters.size()];
		for(int i=0; i<pointsBeingParameters.size(); i++) {
			parameters[i] = valueFromInterpolationEntity(pointsBeingParameters.get(i).value, pointsBeingParameters.get(i).time);
		}
		return parameters;
	}


	@Override
	public void setParameter(RandomVariable[] parameter) {
		throw new UnsupportedOperationException("This class is immutable. Use getCloneForParameter(double[]) instead.");
	}

	private void setParameterPrivate(RandomVariable[] parameter) {
		for(int i=0; i<pointsBeingParameters.size(); i++) {
			pointsBeingParameters.get(i).value = interpolationEntityFromValue(parameter[i], pointsBeingParameters.get(i).time);
		}
		rationalFunctionInterpolation = null;
		curveCacheReference = null;
	}

	private RandomVariable interpolationEntityFromValue(RandomVariable value, double time) {
		switch(interpolationEntity) {
		case VALUE:
		default:
			return value;
		case LOG_OF_VALUE:
			return value.floor(0.0).log();
		case LOG_OF_VALUE_PER_TIME:
			if(time == 0) {
				throw new IllegalArgumentException("The interpolation method LOG_OF_VALUE_PER_TIME does not allow to add a value at time = 0.");
			} else {
				return  value.floor(0.0).log().div(time);
			}
		}
	}

	private RandomVariable valueFromInterpolationEntity(RandomVariable interpolationEntityValue, double time) {
		switch(interpolationEntity) {
		case VALUE:
		default:
			return interpolationEntityValue;
		case LOG_OF_VALUE:
			return interpolationEntityValue.exp();
		case LOG_OF_VALUE_PER_TIME:
			return interpolationEntityValue.mult(time).exp();
		}
	}

	@Override
	public CurveInterpolation clone() throws CloneNotSupportedException {
		CurveInterpolation newCurve = (CurveInterpolation) super.clone();

		newCurve.points					= new ArrayList<>();
		newCurve.pointsBeingParameters	= new ArrayList<>();
		newCurve.rationalFunctionInterpolation = null;
		newCurve.curveCacheReference = null;
		for(Point point : points) {
			Point newPoint = (Point) point.clone();
			newCurve.points.add(newPoint);
			if(point.isParameter) {
				newCurve.pointsBeingParameters.add(newPoint);
			}
		}

		return newCurve;
	}

	@Override
	public Curve getCloneForParameter(RandomVariable[] parameter) throws CloneNotSupportedException {
		if(Arrays.equals(parameter, getParameter())) {
			return this;
		}
		CurveInterpolation newCurve = this.clone();
		newCurve.setParameterPrivate(parameter);

		return newCurve;
	}

	@Override
	public CurveBuilder getCloneBuilder() throws CloneNotSupportedException {
		Builder builder = new Builder(this);
		return builder;
	}

	@Override
	public String toString() {
		/*
		 * Pretty print curveFromInterpolationPoints (appended to standard toString)
		 */
		StringBuilder curveTableString = new StringBuilder();
		NumberFormat formatTime = new DecimalFormat("0.00000000E0");	// Floating point time is accurate to 3+5 digits.
		for (Point point : points) {
			curveTableString.append(formatTime.format(point.time) + "\t");
			curveTableString.append(FloatingpointDate.getDateFromFloatingPointDate(getReferenceDate(), point.time) + "\t");
			curveTableString.append(valueFromInterpolationEntity(point.value, point.time) + "\n");
		}

		return "CurveFromInterpolationPoints [points=" + points + ", pointsBeingParameters=" + pointsBeingParameters + ", interpolationMethod="
		+ interpolationMethod + ", extrapolationMethod=" + extrapolationMethod + ", interpolationEntity="
		+ interpolationEntity + ", rationalFunctionInterpolation=" + rationalFunctionInterpolation
		+ ", toString()=" + super.toString() + ",\n" + curveTableString + "]";
	}

	private void readObject(java.io.ObjectInputStream in) throws ClassNotFoundException, IOException {
		in.defaultReadObject();
		// initialization of transients
		rationalFunctionInterpolationLazyInitLock = new Object();
	}
}
