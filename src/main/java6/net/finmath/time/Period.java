/*
 * (c) Copyright Christian P. Fries, Germany. Contact: email@christian-fries.de.
 *
 * Created on 07.09.2013
 */

package net.finmath.time;

import org.threeten.bp.LocalDate;

/**
 * A period, i.e. a time interval suitable for securities with regular payment schedules.
 *
 * For example, the accrual period is usually given by the interval periodStart, periodEnd, where as fixing date and payment date
 * can be adjusted versions of periodStart and periodsEnd, e.g. via fixing offsets and payment offsets.
 *
 * Period implement the <code>Comparable</code> interface by simply using getPeriodEnd().compareTo(), i.e., the ordering is
 * determined by periodEnd only.
 *
 * For a list of subsequent (sorted) periods it is often assumed that periodStart agrees with the periodEnd of the preceeding period,
 * resulting in a time-discretization.
 *
 * @author Christian Fries
 * @version 1.0
 */
public class Period implements Comparable<Period> {

	private LocalDate fixing;
	private LocalDate payment;
	private LocalDate periodStart;
	private LocalDate periodEnd;

	public Period(LocalDate fixing,  LocalDate payment,
			LocalDate periodStart, LocalDate periodEnd) {
		super();
		this.fixing = fixing;
		this.payment = payment;
		this.periodStart = periodStart;
		this.periodEnd = periodEnd;
	}

	public LocalDate getFixing() {
		return fixing;
	}

	public LocalDate getPayment() {
		return payment;
	}

	public LocalDate getPeriodStart() {
		return periodStart;
	}

	public LocalDate getPeriodEnd() {
		return periodEnd;
	}

	@Override
	public int compareTo(Period o) {
		return getPeriodEnd().compareTo(o.getPeriodEnd());
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((fixing == null) ? 0 : fixing.hashCode());
		result = prime * result + ((payment == null) ? 0 : payment.hashCode());
		result = prime * result + ((periodEnd == null) ? 0 : periodEnd.hashCode());
		result = prime * result + ((periodStart == null) ? 0 : periodStart.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		Period other = (Period) obj;
		if (fixing == null) {
			if (other.fixing != null) {
				return false;
			}
		} else if (!fixing.equals(other.fixing)) {
			return false;
		}
		if (payment == null) {
			if (other.payment != null) {
				return false;
			}
		} else if (!payment.equals(other.payment)) {
			return false;
		}
		if (periodEnd == null) {
			if (other.periodEnd != null) {
				return false;
			}
		} else if (!periodEnd.equals(other.periodEnd)) {
			return false;
		}
		if (periodStart == null) {
			if (other.periodStart != null) {
				return false;
			}
		} else if (!periodStart.equals(other.periodStart)) {
			return false;
		}
		return true;
	}

	@Override
	public String toString() {
		return "Period [start=" + periodStart + ", end=" + periodEnd + ", fixing=" + fixing + ", payment=" + payment + "]";
	}
}
