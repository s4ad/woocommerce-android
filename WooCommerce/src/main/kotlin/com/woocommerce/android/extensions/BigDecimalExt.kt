package com.woocommerce.android.extensions

import java.math.BigDecimal

infix fun BigDecimal?.isEqualTo(x: BigDecimal?) = this == x || (x != null && this?.compareTo(x) == 0)

infix fun BigDecimal?.isNotEqualTo(x: BigDecimal?) = !this.isEqualTo(x)

infix fun BigDecimal?.isEquivalentTo(that: BigDecimal?): Boolean {
    val val1 = this ?: BigDecimal.ZERO
    val val2 = that ?: BigDecimal.ZERO
    return val1.isEqualTo(val2)
}

fun BigDecimal?.isNotSet(): Boolean = this.isEquivalentTo(BigDecimal.ZERO)

fun BigDecimal?.isSet(): Boolean = !this.isNotSet()

inline fun <T> Iterable<T>.sumByBigDecimal(selector: (T) -> BigDecimal): BigDecimal {
    var sum = BigDecimal.ZERO
    for (element in this) {
        sum += selector(element)
    }
    return sum
}

inline fun <T> Iterable<T>.sumByFloat(selector: (T) -> Float): Float {
    var sum = 0f
    for (element in this) {
        sum += selector(element)
    }
    return sum
}
