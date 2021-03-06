package net.finmath.singleswaprate.data;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.TreeSet;

import net.finmath.time.SchedulePrototype;


/**
 * A basic implementation of DataTable, which only allows access to data via int and provides no means of inter- or extrapolation.
 *
 * @author Christian Fries
 * @author Roland Bachl
 *
 */
public class DataTableLight implements DataTable, Cloneable {

	private static final long serialVersionUID = -8655513677146846285L;
	private final String name;
	private final TableConvention convention;

	private final TreeSet<Integer> maturitySet = new TreeSet<>();
	private final TreeSet<Integer> terminationSet = new TreeSet<>();
	private final HashMap<DataKey, Double> entries = new HashMap<>();


	/**
	 * Create an empty table.
	 *
	 * @param name The name of the table.
	 * @param tableConvention The convention of the table.
	 */
	public DataTableLight(String name, TableConvention tableConvention){
		this.name = name;
		this.convention = tableConvention;
	}

	/**
	 * Create a table.
	 *
	 * @param name The name of the table.
	 * @param tableConvention The convention of the table.
	 * @param maturities The maturities of the points as offset with respect to the reference date.
	 * @param terminations The terminations of the points as offset with respect to the maturity date.
	 * @param values The values at the points.
	 */
	public DataTableLight(String name, TableConvention tableConvention, int[] maturities, int[] terminations, double[] values){
		this(name, tableConvention);

		if(maturities.length != terminations.length || maturities.length != values.length ) {
			throw new IllegalArgumentException("Number of entries need to match. Input was maturities: "+maturities.length +", terminations: "+
					terminations.length +",values: " +values.length+". ");
		}

		for(int index = 0; index < maturities.length; index++) {
			entries.put(new DataKey(maturities[index], terminations[index]), values[index]);
			maturitySet.add(maturities[index]);
			terminationSet.add(terminations[index]);
		}
	}

	/**
	 * Create a table.
	 *
	 * @param name The name of the table.
	 * @param tableConvention The convention of the table.
	 * @param maturities The maturities of the points as offset with respect to the reference date.
	 * @param terminations The terminations of the points as offset with respect to the maturity date.
	 * @param values The values at the points.
	 */
	public DataTableLight(String name, TableConvention tableConvention, List<Integer> maturities, List<Integer> terminations, List<Double> values) {
		this(name, tableConvention);

		if(maturities.size() != terminations.size() || maturities.size() != values.size() ) {
			throw new IllegalArgumentException("Number of entries need to match. Input was maturities: "+maturities.size() +", terminations: "+
					terminations.size() +",values: " +values.size()+". ");
		}

		for(int index = 0; index < maturities.size(); index++) {
			int mat = maturities.get(index);
			int term = terminations.get(index);
			double val =values.get(index);

			entries.put(new DataKey(mat, term), val);
			maturitySet.add(mat);
			terminationSet.add(term);
		}
	}

	@Override
	public DataTableLight addPoint(int maturity, int termination, double value) {
		DataTableLight newTable = clone();
		newTable.entries.put(new DataKey(maturity, termination),value);
		newTable.maturitySet.add(maturity);
		newTable.terminationSet.add(termination);
		return newTable;
	}

	@Override
	public DataTableLight addPoints(int[] maturities, int[] terminations, double[] values) {
		DataTableLight newTable = clone();
		for(int index = 0; index < maturities.length; index++) {
			newTable.entries.put(new DataKey(maturities[index], terminations[index]), values[index]);
			newTable.maturitySet.add(maturities[index]);
			newTable.terminationSet.add(terminations[index]);
		}
		return newTable;
	}

	@Override
	public double getValue(int maturity, int termination) {
		DataKey key = new DataKey(maturity, termination);
		if(entries.containsKey(key)) {
			return entries.get(new DataKey(maturity,termination));
		} else {
			throw new NullPointerException("Key "+key.toString()+" not found in table " +name);
		}
	}

	@Override
	public boolean containsEntryFor(int maturity, int termination) {
		return entries.containsKey(new DataKey(maturity, termination));
	}

	@Override
	public TreeSet<Integer> getMaturities(){
		return new TreeSet<>(maturitySet);
	}

	@Override
	public TreeSet<Integer> getTerminations(){
		return new TreeSet<>(terminationSet);
	}

	@Override
	public TreeSet<Integer> getTerminationsForMaturity(int maturity){
		if(maturitySet.contains(maturity)) {
			TreeSet<Integer> returnSet = new TreeSet<>();
			for(int termination:terminationSet) {
				if(entries.containsKey(new DataKey(maturity,termination))) {
					returnSet.add(termination);
				}
			}
			return returnSet;
		}
		//		else throw new IllegalArgumentException("This data table does not contain entries for maturity "+maturity);
		return new TreeSet<>();
	}

	@Override
	public TreeSet<Integer> getMaturitiesForTermination(int termination) {
		if(terminationSet.contains(termination)) {
			TreeSet<Integer> returnSet = new TreeSet<>();
			for(int maturity: maturitySet) {
				if(entries.containsKey(new DataKey(maturity,termination))) {
					returnSet.add(maturity);
				}
			}
			return returnSet;
		}
		//		else throw new IllegalArgumentException("This data table does not contain entries for termination " +termination);
		return new TreeSet<>();
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public TableConvention getConvention() {
		return convention;
	}

	@Override
	public DataTableLight clone() {
		DataTableLight newTable = new DataTableLight(name, convention);
		newTable.entries.putAll(entries);
		newTable.maturitySet.addAll(maturitySet);
		newTable.terminationSet.addAll(terminationSet);
		return newTable;
	}

	@Override
	public String toString() {
		return toString(1.0);
	}

	/**
	 * Transforms the table into a human readable String.
	 *
	 * @param unit A value with which each entry of the table is multiplied before displaying.
	 * @return A String representation of the table.
	 */
	public String toString(double unit) {
		StringBuilder builder = new StringBuilder();
		builder.append("Name: " +name+ ", TableConvention: " +convention+ ",\n");
		for(int termination : terminationSet) {
			builder.append("\t"+termination);
		}
		for(int maturity: maturitySet) {
			builder.append("\n"+maturity);
			for(int termination:terminationSet) {
				DataKey key = new DataKey(maturity, termination);
				builder.append('\t');
				if(entries.containsKey(key)) {
					builder.append(entries.get(key) * unit);
				}
			}
		}

		return builder.toString();
	}

	@Override
	public int size() {
		return entries.size();
	}

	/**
	 * Nested class to use as key in values map.
	 *
	 * @author Christian Fries
	 * @author Roland Bachl
	 *
	 */
	private static class DataKey implements Serializable {

		private static final long serialVersionUID = 3391432439373885684L;
		private int maturity;
		private int termination;

		/**
		 * Create key from int.
		 *
		 * @param maturity
		 * @param termination
		 */
		DataKey(int maturity, int termination){
			this.maturity = maturity;
			this.termination = termination;
		}

		@Override
		public boolean equals(Object other) {
			if(this == other) {
				return true;
			}

			if(other == null) {
				return false;
			}
			if(other.getClass() != getClass()) {
				return false;
			}

			if(maturity 	!= ((DataKey) other).maturity ) {
				return false;
			}
			return termination == ((DataKey) other).termination;
		}

		@Override
		public int hashCode() {
			return maturity*termination;
		}

		@Override
		public String toString() {
			return "DataKey [maturity=" + maturity + ", termination=" + termination + "]";
		}
	}

	@Override
	public double getValue(double maturity, double termination) {
		throw new UnsupportedOperationException("Table " +getName()+ " of class "+getClass()+" does not support access via double.");
	}

	@Override
	public boolean containsEntryFor(double maturity, double termination) {
		throw new UnsupportedOperationException("Table " +getName()+ " of class "+getClass()+" does not support access via double.");
	}

	@Override
	public LocalDate getReferenceDate() {
		return null;
	}

	@Override
	public SchedulePrototype getScheduleMetaData() {
		return null;
	}

}
