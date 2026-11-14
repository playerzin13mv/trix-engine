package com.trix.engine.lang

// ============================================================
//  TrixScript — a linguagem da Trix Engine
//  Lexer -> Parser (AST) -> Interpretador
// ============================================================

class TrixError(msg: String, val line: Int = 0) :
    RuntimeException(if (line > 0) "linha $line: $msg" else msg)

interface Props {
    fun get(n: String): Any?
    fun set(n: String, v: Any?)
}

// ---------------- Lexer ----------------
enum class T { NUM, STR, ID, KW, OP, NL, EOF }
data class Token(val t: T, val s: String, val line: Int)

val KEYWORDS = setOf(
    "var", "func", "end", "if", "elif", "else", "while", "for", "in",
    "return", "break", "and", "or", "not", "true", "false", "null"
)
private val TWO_OPS = setOf("==", "!=", "<=", ">=", "..", "+=", "-=", "*=", "/=")

fun lex(src: String): List<Token> {
    val out = ArrayList<Token>()
    var i = 0
    var line = 1
    while (i < src.length) {
        val c = src[i]
        when {
            c == '\n' -> { out.add(Token(T.NL, "\n", line)); line++; i++ }
            c == ' ' || c == '\t' || c == '\r' -> i++
            c == '#' -> { while (i < src.length && src[i] != '\n') i++ }
            c.isDigit() -> {
                val s = i
                while (i < src.length && (src[i].isDigit() ||
                            (src[i] == '.' && i + 1 < src.length && src[i + 1].isDigit()))) i++
                out.add(Token(T.NUM, src.substring(s, i), line))
            }
            c.isLetter() || c == '_' -> {
                val s = i
                while (i < src.length && (src[i].isLetterOrDigit() || src[i] == '_')) i++
                val w = src.substring(s, i)
                out.add(Token(if (w in KEYWORDS) T.KW else T.ID, w, line))
            }
            c == '"' -> {
                i++
                val sb = StringBuilder()
                while (i < src.length && src[i] != '"' && src[i] != '\n') {
                    if (src[i] == '\\' && i + 1 < src.length) {
                        i++
                        sb.append(when (src[i]) { 'n' -> '\n'; 't' -> '\t'; else -> src[i] })
                    } else sb.append(src[i])
                    i++
                }
                if (i >= src.length || src[i] != '"') throw TrixError("texto sem aspas no final", line)
                i++
                out.add(Token(T.STR, sb.toString(), line))
            }
            else -> {
                val two = if (i + 1 < src.length) src.substring(i, i + 2) else ""
                if (two in TWO_OPS) { out.add(Token(T.OP, two, line)); i += 2 }
                else if (c in "+-*/%<>=(),.[]") { out.add(Token(T.OP, c.toString(), line)); i++ }
                else throw TrixError("caractere inválido '$c'", line)
            }
        }
    }
    out.add(Token(T.NL, "\n", line))
    out.add(Token(T.EOF, "", line))
    return out
}

// ---------------- AST ----------------
sealed class Expr
class Lit(val v: Any?) : Expr()
class Id(val n: String, val line: Int) : Expr()
class Member(val o: Expr, val n: String, val line: Int) : Expr()
class Call(val f: Expr, val args: List<Expr>, val line: Int) : Expr()
class Bin(val op: String, val l: Expr, val r: Expr, val line: Int) : Expr()
class Un(val op: String, val e: Expr, val line: Int) : Expr()
class ListLit(val items: List<Expr>) : Expr()
class Index(val o: Expr, val i: Expr, val line: Int) : Expr()

sealed class Stmt
class VarS(val n: String, val e: Expr) : Stmt()
class AssignS(val target: Expr, val op: String, val e: Expr, val line: Int) : Stmt()
class ExprS(val e: Expr) : Stmt()
class IfS(val branches: List<Pair<Expr, List<Stmt>>>, val els: List<Stmt>?) : Stmt()
class WhileS(val c: Expr, val body: List<Stmt>) : Stmt()
class ForS(val v: String, val a: Expr, val b: Expr?, val body: List<Stmt>, val line: Int) : Stmt()
class FuncS(val name: String, val params: List<String>, val body: List<Stmt>) : Stmt()
class RetS(val e: Expr?) : Stmt()
object BreakS : Stmt()

// ---------------- Parser ----------------
class Parser(private val t: List<Token>) {
    private var p = 0
    private fun peek() = t[p]
    private fun next() = t[p++]
    private fun isOp(s: String) = peek().t == T.OP && peek().s == s
    private fun isKw(s: String) = peek().t == T.KW && peek().s == s
    private fun matchOp(s: String): Boolean { if (isOp(s)) { p++; return true }; return false }
    private fun expectOp(s: String) { if (!isOp(s)) throw TrixError("esperava '$s'", peek().line); p++ }
    private fun expectEnd() { if (!isKw("end")) throw TrixError("faltou 'end'", peek().line); p++ }
    private fun skipNl() { while (peek().t == T.NL) p++ }
    private fun ident(): String {
        val k = next()
        if (k.t != T.ID) throw TrixError("esperava um nome", k.line)
        return k.s
    }

    fun program(): List<Stmt> {
        val r = block(emptySet())
        if (peek().t != T.EOF) throw TrixError("'${peek().s}' inesperado", peek().line)
        return r
    }

    private fun block(stop: Set<String>): List<Stmt> {
        val out = ArrayList<Stmt>()
        while (true) {
            skipNl()
            val k = peek()
            if (k.t == T.EOF) break
            if (k.t == T.KW && k.s in stop) break
            out.add(stmt())
            if (peek().t != T.NL && peek().t != T.EOF)
                throw TrixError("fim de linha esperado, achei '${peek().s}'", peek().line)
        }
        return out
    }

    private fun stmt(): Stmt {
        val k = peek()
        if (k.t == T.KW) {
            when (k.s) {
                "var" -> { p++; val n = ident(); expectOp("="); return VarS(n, expr()) }
                "func" -> {
                    p++
                    val n = ident()
                    expectOp("(")
                    val ps = ArrayList<String>()
                    if (!isOp(")")) { do { ps.add(ident()) } while (matchOp(",")) }
                    expectOp(")")
                    val b = block(setOf("end")); expectEnd()
                    return FuncS(n, ps, b)
                }
                "if" -> {
                    p++
                    val br = ArrayList<Pair<Expr, List<Stmt>>>()
                    var c = expr()
                    var b = block(setOf("elif", "else", "end"))
                    br.add(c to b)
                    var els: List<Stmt>? = null
                    while (true) {
                        if (isKw("elif")) { p++; c = expr(); b = block(setOf("elif", "else", "end")); br.add(c to b) }
                        else if (isKw("else")) { p++; els = block(setOf("end")) }
                        else break
                    }
                    expectEnd()
                    return IfS(br, els)
                }
                "while" -> { p++; val c = expr(); val b = block(setOf("end")); expectEnd(); return WhileS(c, b) }
                "for" -> {
                    p++
                    val v = ident()
                    if (!isKw("in")) throw TrixError("esperava 'in'", peek().line)
                    p++
                    val a = expr()
                    var b2: Expr? = null
                    if (isOp("..")) { p++; b2 = expr() }
                    val body = block(setOf("end")); expectEnd()
                    return ForS(v, a, b2, body, k.line)
                }
                "return" -> { p++; return if (peek().t == T.NL) RetS(null) else RetS(expr()) }
                "break" -> { p++; return BreakS }
            }
        }
        val e = expr()
        val a = peek()
        if (a.t == T.OP && a.s in setOf("=", "+=", "-=", "*=", "/=")) {
            p++
            if (e !is Id && e !is Member && e !is Index) throw TrixError("não dá para atribuir aqui", a.line)
            return AssignS(e, a.s, expr(), a.line)
        }
        return ExprS(e)
    }

    fun expr(): Expr = orE()
    private fun orE(): Expr { var l = andE(); while (isKw("or")) { val ln = next().line; l = Bin("or", l, andE(), ln) }; return l }
    private fun andE(): Expr { var l = notE(); while (isKw("and")) { val ln = next().line; l = Bin("and", l, notE(), ln) }; return l }
    private fun notE(): Expr { if (isKw("not")) { val ln = next().line; return Un("not", notE(), ln) }; return eqE() }
    private fun eqE(): Expr { var l = cmpE(); while (isOp("==") || isOp("!=")) { val o = next(); l = Bin(o.s, l, cmpE(), o.line) }; return l }
    private fun cmpE(): Expr {
        var l = addE()
        while (isOp("<") || isOp(">") || isOp("<=") || isOp(">=")) { val o = next(); l = Bin(o.s, l, addE(), o.line) }
        return l
    }
    private fun addE(): Expr { var l = mulE(); while (isOp("+") || isOp("-")) { val o = next(); l = Bin(o.s, l, mulE(), o.line) }; return l }
    private fun mulE(): Expr {
        var l = unary()
        while (isOp("*") || isOp("/") || isOp("%")) { val o = next(); l = Bin(o.s, l, unary(), o.line) }
        return l
    }
    private fun unary(): Expr { if (isOp("-")) { val o = next(); return Un("-", unary(), o.line) }; return postfix() }
    private fun postfix(): Expr {
        var e = primary()
        while (true) {
            if (isOp(".")) {
                next()
                val n = next()
                if (n.t != T.ID) throw TrixError("esperava um nome depois do '.'", n.line)
                e = Member(e, n.s, n.line)
            } else if (isOp("[")) {
                val o = next()
                val i = expr()
                expectOp("]")
                e = Index(e, i, o.line)
            } else if (isOp("(")) {
                val o = next()
                val args = ArrayList<Expr>()
                if (!isOp(")")) { do { args.add(expr()) } while (matchOp(",")) }
                expectOp(")")
                e = Call(e, args, o.line)
            } else break
        }
        return e
    }
    private fun primary(): Expr {
        val k = next()
        return when {
            k.t == T.NUM -> Lit(k.s.toDouble())
            k.t == T.STR -> Lit(k.s)
            k.t == T.ID -> Id(k.s, k.line)
            k.t == T.KW && k.s == "true" -> Lit(true)
            k.t == T.KW && k.s == "false" -> Lit(false)
            k.t == T.KW && k.s == "null" -> Lit(null)
            k.t == T.OP && k.s == "(" -> { val e = expr(); expectOp(")"); e }
            k.t == T.OP && k.s == "[" -> {
                val items = ArrayList<Expr>()
                if (!isOp("]")) { do { items.add(expr()) } while (matchOp(",")) }
                expectOp("]")
                ListLit(items)
            }
            else -> throw TrixError("'${if (k.t == T.NL) "fim de linha" else k.s}' inesperado aqui", k.line)
        }
    }
}

// ---------------- Interpretador ----------------
private class ReturnEx(val v: Any?) : RuntimeException(null, null, false, false)
private object BreakEx : RuntimeException(null, null, false, false)

class Env(val parent: Env?) {
    val vars = HashMap<String, Any?>()
    fun find(n: String): Env? {
        var e: Env? = this
        while (e != null) { if (e.vars.containsKey(n)) return e; e = e.parent }
        return null
    }
}

fun fmt(v: Any?): String = when (v) {
    null -> "null"
    is Double -> if (v == Math.floor(v) && Math.abs(v) < 1e15) v.toLong().toString() else v.toString()
    is List<*> -> v.joinToString(", ", "[", "]") { fmt(it) }
    else -> v.toString()
}

fun typeName(v: Any?): String = when (v) {
    null -> "nulo"; is Double -> "número"; is String -> "texto"; is Boolean -> "booleano"
    is Props -> "objeto"; is List<*> -> "lista"; else -> "valor"
}

class Interpreter(private val log: (String) -> Unit) {
    val globals = Env(null)
    val builtins = HashMap<String, (List<Any?>) -> Any?>()
    private val funcs = HashMap<String, FuncS>()
    private val rnd = java.util.Random()
    private var budget = 0
    private var depth = 0

    private fun a(args: List<Any?>, i: Int): Double =
        (args.getOrNull(i) as? Double) ?: throw TrixError("argumento ${i + 1} deve ser número")
    private fun g(o: Props, n: String): Double = (o.get(n) as? Double) ?: 0.0

    init {
        val b = builtins
        b["print"] = { x -> log(x.joinToString(" ") { fmt(it) }); null }
        b["sin"] = { x -> Math.sin(a(x, 0)) }
        b["cos"] = { x -> Math.cos(a(x, 0)) }
        b["atan2"] = { x -> Math.atan2(a(x, 0), a(x, 1)) }
        b["abs"] = { x -> Math.abs(a(x, 0)) }
        b["sqrt"] = { x -> Math.sqrt(a(x, 0)) }
        b["pow"] = { x -> Math.pow(a(x, 0), a(x, 1)) }
        b["floor"] = { x -> Math.floor(a(x, 0)) }
        b["ceil"] = { x -> Math.ceil(a(x, 0)) }
        b["round"] = { x -> Math.round(a(x, 0)).toDouble() }
        b["min"] = { x -> Math.min(a(x, 0), a(x, 1)) }
        b["max"] = { x -> Math.max(a(x, 0), a(x, 1)) }
        b["clamp"] = { x -> Math.max(a(x, 1), Math.min(a(x, 2), a(x, 0))) }
        b["lerp"] = { x -> a(x, 0) + (a(x, 1) - a(x, 0)) * a(x, 2) }
        b["random"] = { x -> if (x.size >= 2) a(x, 0) + rnd.nextDouble() * (a(x, 1) - a(x, 0)) else rnd.nextDouble() }
        b["str"] = { x -> fmt(x.getOrNull(0)) }
        b["overlaps"] = { x ->
            val p = x.getOrNull(0)
            val q = x.getOrNull(1)
            if (p !is Props || q !is Props) throw TrixError("overlaps(a, b) precisa de 2 objetos")
            Math.abs(g(p, "x") - g(q, "x")) * 2 < g(p, "w") + g(q, "w") &&
                    Math.abs(g(p, "y") - g(q, "y")) * 2 < g(p, "h") + g(q, "h")
        }
        b["dist"] = { x ->
            val p = x.getOrNull(0)
            val q = x.getOrNull(1)
            if (p !is Props || q !is Props) throw TrixError("dist(a, b) precisa de 2 objetos")
            Math.hypot(g(p, "x") - g(q, "x"), g(p, "y") - g(q, "y"))
        }
        b["destroy"] = { x -> (x.getOrNull(0) as? Props)?.set("visible", false); null }
        b["dist_xy"] = { x -> Math.hypot(a(x, 0) - a(x, 2), a(x, 1) - a(x, 3)) }
        b["sign"] = { x -> Math.signum(a(x, 0)) }
        b["rad"] = { x -> Math.toRadians(a(x, 0)) }
        b["deg"] = { x -> Math.toDegrees(a(x, 0)) }
        b["tan"] = { x -> Math.tan(a(x, 0)) }
        b["asin"] = { x -> Math.asin(a(x, 0)) }
        b["acos"] = { x -> Math.acos(a(x, 0)) }
        b["approach"] = { x ->
            val v = a(x, 0); val t = a(x, 1); val s = Math.abs(a(x, 2))
            if (v < t) Math.min(v + s, t) else Math.max(v - s, t)
        }
        b["len"] = { x ->
            when (val v = x.getOrNull(0)) {
                is List<*> -> v.size.toDouble()
                is String -> v.length.toDouble()
                else -> throw TrixError("len(x) precisa de lista ou texto")
            }
        }
        b["push"] = { x ->
            @Suppress("UNCHECKED_CAST")
            val l = x.getOrNull(0) as? MutableList<Any?> ?: throw TrixError("push(lista, valor) precisa de uma lista")
            l.add(x.getOrNull(1)); null
        }
        b["pop"] = { x ->
            @Suppress("UNCHECKED_CAST")
            val l = x.getOrNull(0) as? MutableList<Any?> ?: throw TrixError("pop(lista) precisa de uma lista")
            if (l.isEmpty()) null else l.removeAt(l.size - 1)
        }
        b["remove"] = { x ->
            @Suppress("UNCHECKED_CAST")
            val l = x.getOrNull(0) as? MutableList<Any?> ?: throw TrixError("remove(lista, valor) precisa de uma lista")
            l.remove(x.getOrNull(1))
        }
        b["contains"] = { x ->
            val l = x.getOrNull(0) as? List<*> ?: throw TrixError("contains(lista, valor) precisa de uma lista")
            l.contains(x.getOrNull(1))
        }
    }

    fun load(src: String) {
        val prog = Parser(lex(src)).program()
        budget = 2_000_000
        depth = 0
        execBlock(prog, globals)
    }

    fun has(n: String) = funcs.containsKey(n)

    fun call(n: String, args: List<Any?>): Any? {
        budget = 300_000
        depth = 0
        return callFunc(funcs[n] ?: throw TrixError("função '$n' não existe"), args)
    }

    private fun callFunc(f: FuncS, args: List<Any?>): Any? {
        val env = Env(globals)
        f.params.forEachIndexed { i, p -> env.vars[p] = args.getOrNull(i) }
        return try { execBlock(f.body, env); null } catch (r: ReturnEx) { r.v }
    }

    private fun tick(line: Int = 0) {
        if (--budget < 0) throw TrixError("o script demorou demais (loop infinito?)", line)
    }

    private fun execBlock(b: List<Stmt>, env: Env) { for (s in b) exec(s, env) }

    private fun exec(s: Stmt, env: Env) {
        tick()
        when (s) {
            is VarS -> env.vars[s.n] = eval(s.e, env)
            is AssignS -> {
                var v = eval(s.e, env)
                if (s.op != "=") v = binop(s.op.dropLast(1), eval(s.target, env), v, s.line)
                assign(s.target, v, env)
            }
            is ExprS -> eval(s.e, env)
            is IfS -> {
                for ((c, b) in s.branches) {
                    if (truthy(eval(c, env))) { execBlock(b, Env(env)); return }
                }
                s.els?.let { execBlock(it, Env(env)) }
            }
            is WhileS -> try {
                while (truthy(eval(s.c, env))) { tick(); execBlock(s.body, Env(env)) }
            } catch (e: BreakEx) { }
            is ForS -> {
                if (s.b == null) {
                    val lst = eval(s.a, env) as? List<*> ?: throw TrixError("for ... in precisa de uma lista ou de a..b", s.line)
                    try {
                        for (item in ArrayList(lst)) {
                            tick()
                            val e = Env(env); e.vars[s.v] = item
                            execBlock(s.body, e)
                        }
                    } catch (e: BreakEx) { }
                } else {
                    val lo = num(eval(s.a, env), s.line).toInt()
                    val hi = num(eval(s.b, env), s.line).toInt()
                    try {
                        for (i in lo..hi) {
                            tick()
                            val e = Env(env); e.vars[s.v] = i.toDouble()
                            execBlock(s.body, e)
                        }
                    } catch (e: BreakEx) { }
                }
            }
            is FuncS -> funcs[s.name] = s
            is RetS -> throw ReturnEx(s.e?.let { eval(it, env) })
            BreakS -> throw BreakEx
        }
    }

    private fun assign(t: Expr, v: Any?, env: Env) {
        when (t) {
            is Id -> {
                val h = env.find(t.n) ?: throw TrixError("'${t.n}' não existe. Use: var ${t.n} = ...", t.line)
                h.vars[t.n] = v
            }
            is Member -> {
                val o = eval(t.o, env)
                if (o is Props) o.set(t.n, v) else throw TrixError("não dá para mudar '${t.n}'", t.line)
            }
            is Index -> {
                val o = eval(t.o, env)
                val i = num(eval(t.i, env), t.line).toInt()
                @Suppress("UNCHECKED_CAST")
                val l = o as? MutableList<Any?> ?: throw TrixError("só dá para mudar itens de uma lista", t.line)
                if (i < 0 || i >= l.size) throw TrixError("índice $i fora da lista (tamanho ${l.size})", t.line)
                l[i] = v
            }
            else -> {}
        }
    }

    private fun eval(e: Expr, env: Env): Any? = when (e) {
        is Lit -> e.v
        is Id -> {
            val h = env.find(e.n)
            if (h != null) h.vars[e.n]
            else if (builtins.containsKey(e.n)) builtins[e.n]
            else throw TrixError("'${e.n}' não existe", e.line)
        }
        is Member -> {
            val o = eval(e.o, env)
            if (o is Props) o.get(e.n) else throw TrixError("'${e.n}' não existe em ${typeName(o)}", e.line)
        }
        is Call -> doCall(e, env)
        is ListLit -> ArrayList<Any?>(e.items.map { eval(it, env) })
        is Index -> {
            val o = eval(e.o, env)
            val i = num(eval(e.i, env), e.line).toInt()
            when (o) {
                is List<*> -> { if (i < 0 || i >= o.size) throw TrixError("índice $i fora da lista (tamanho ${o.size})", e.line); o[i] }
                is String -> { if (i < 0 || i >= o.length) throw TrixError("índice $i fora do texto", e.line); o[i].toString() }
                else -> throw TrixError("só listas e textos aceitam [ ]", e.line)
            }
        }
        is Un -> {
            val v = eval(e.e, env)
            if (e.op == "not") !truthy(v) else -num(v, e.line)
        }
        is Bin -> when (e.op) {
            "and" -> { val l = eval(e.l, env); if (!truthy(l)) l else eval(e.r, env) }
            "or" -> { val l = eval(e.l, env); if (truthy(l)) l else eval(e.r, env) }
            else -> binop(e.op, eval(e.l, env), eval(e.r, env), e.line)
        }
    }

    private fun doCall(c: Call, env: Env): Any? {
        val args = c.args.map { eval(it, env) }
        val f = c.f
        if (f is Id) {
            val uf = funcs[f.n]
            if (uf != null) {
                if (++depth > 120) { depth = 0; throw TrixError("recursão demais", c.line) }
                try { return callFunc(uf, args) } finally { if (depth > 0) depth-- }
            }
            val bf = builtins[f.n]
            if (bf != null) return bf(args)
            throw TrixError("função '${f.n}' não existe", c.line)
        }
        throw TrixError("isso não pode ser chamado como função", c.line)
    }

    private fun truthy(v: Any?) = !(v == null || v == false)

    private fun num(v: Any?, line: Int): Double =
        v as? Double ?: throw TrixError("esperava número, veio ${typeName(v)}", line)

    private fun binop(op: String, a: Any?, b: Any?, line: Int): Any? = when (op) {
        "+" -> if (a is Double && b is Double) a + b
        else if (a is String || b is String) fmt(a) + fmt(b)
        else throw TrixError("não dá para somar ${typeName(a)} com ${typeName(b)}", line)
        "-" -> num(a, line) - num(b, line)
        "*" -> num(a, line) * num(b, line)
        "/" -> { val d = num(b, line); if (d == 0.0) throw TrixError("divisão por zero", line); num(a, line) / d }
        "%" -> { val d = num(b, line); if (d == 0.0) throw TrixError("divisão por zero", line); num(a, line) % d }
        "==" -> a == b
        "!=" -> a != b
        "<" -> num(a, line) < num(b, line)
        ">" -> num(a, line) > num(b, line)
        "<=" -> num(a, line) <= num(b, line)
        ">=" -> num(a, line) >= num(b, line)
        else -> throw TrixError("operador '$op' desconhecido", line)
    }
}
