package com.github.whyrising.kedn.core

enum class NodeType {
  BigInt
}

data class EdnNode(val value: String, val type: NodeType)
