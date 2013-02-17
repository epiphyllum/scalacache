package com.github.cb372.cache

import scala.language.experimental.macros
import scala.reflect.macros.Context

object Macros {

  def cacheableImpl[A : c.WeakTypeTag](c: Context)(f: c.Expr[A])(cacheConfig: c.Expr[CacheConfig]): c.Expr[A] = {
    import c.universe._

    c.enclosingMethod match {
      case DefDef(mods, methodName, tparams, vparamss, tpt, rhs) => {
      
        /*
         * Gather all the info needed to build the cache key:
         * class name, method name and the method parameters lists
         */
        val classNameExpr: Expr[String] = getClassName(c)
        val methodNameExpr: Expr[String] = c.literal(methodName.toString)
        val paramIdents: List[List[Ident]] = vparamss.map(ps => ps.map(p => Ident(p.name)))
        val paramssTree: Tree = listToTree(c)(paramIdents.map(ps => listToTree(c)(ps)))
        val paramssExpr: Expr[List[List[Any]]] = c.Expr[List[List[Any]]](paramssTree)
        
        reify {
          val key = cacheConfig.splice.keyGenerator.toCacheKey(classNameExpr.splice, methodNameExpr.splice, paramssExpr.splice)
          val cachedValue = cacheConfig.splice.cache.get[A](key)
          cachedValue.fold[A] {
            // cache miss
            val calculatedValue = f.splice
            cacheConfig.splice.cache.put(key, calculatedValue)
            calculatedValue
          } { v =>
            // cache hit
            v
          }
        }
      
      }

      case _ => {
        // not inside a method
        c.abort(c.enclosingPosition, "This macro must be called from within a method, so that it can generate a cache key. TODO: more useful error message")
      }
    }

  }

  private def getClassName(c: Context): c.Expr[String] = {
    import c.universe._
    val className = c.enclosingClass match {
      case ClassDef(_, name, _, _) => name.toString
      case ModuleDef(_, name, _) => name.toString
      case _ => "" // not inside a class or a module. package object, REPL, somewhere else weird
    }
    c.literal(className)
  }

    /**
     * Convert a List[Tree] to a Tree by calling scala.collection.immutable.list.apply()
     */
    private def listToTree(c: Context)(ts: List[c.Tree]): c.Tree = { 
      import c.universe._
      Apply(Select(Select(Select(Select(Ident("scala"), newTermName("collection")), newTermName("immutable")), newTermName("List")), newTermName("apply")), ts)
    }

}