package com.github.whyrising.kedn.core

enum class NodeType {
  BigInt,
  BigDecimal,
  Symbol
}

data class EdnNode(val value: String, val type: NodeType)
