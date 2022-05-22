package com.github.whyrising.kedn.core

enum class NodeType {
  BigInt,
  BigDecimal
}

data class EdnNode(val value: String, val type: NodeType)
