package uk.neilgall.rulesapp

import io.kotlintest.matchers.shouldEqual
import io.kotlintest.specs.StringSpec

private fun s(s: String) = Term.String<String>(s)
private fun n(n: Int) = Term.Number<String>(n)
private fun a(a: String) = Term.Attribute(a)

class TermParserSpec : StringSpec({
    "string" {
        parse(term, "\"foo\"") shouldEqual Term.String<String>("foo")
    }

    "number" {
        parse(term, "123") shouldEqual Term.Number<String>(123)
    }

    "attribute" {
        parse(term, "foo") shouldEqual Term.Attribute("foo")
    }

    "plus expression" {
        parse(term, "1 + 2") shouldEqual Term.Expr<String>(Term.Number(1), Operator.PLUS, Term.Number(2))
    }

    "minus expression" {
        parse(term, "9 - 5") shouldEqual Term.Expr<String>(Term.Number(9), Operator.MINUS, Term.Number(5))
    }

    "times expression" {
        parse(term, "foo * 7") shouldEqual Term.Expr(Term.Attribute("foo"), Operator.MULTIPLY, Term.Number(7))
    }

    "divide expression" {
        parse(term, "100 / 5") shouldEqual Term.Expr<String>(Term.Number(100), Operator.DIVIDE, Term.Number(5))
    }

    "complex expression" {
        parse(term, "3 + foo * 6") shouldEqual Term.Expr(
                Term.Number(3),
                Operator.PLUS,
                Term.Expr(Term.Attribute("foo"), Operator.MULTIPLY, Term.Number(6))
        )
    }

    "coercion" {
        parse(term, "string 3") shouldEqual Term.Coerce<String>(Term.Number(3), ValueType.STRING)
    }

    "request term" {
        parse(term, "request \"A.B.C\"") shouldEqual Term.Request<String>("A.B.C")
    }

    "rest term" {
        parse(term, "GET \"http://foo.bar\" user=UserName, pass=Password") shouldEqual
                Term.REST("http://foo.bar", RESTMethod.GET, mapOf("user" to "UserName", "pass" to "Password"))
    }
})

class AttributeParserSpec : StringSpec({
    "attribute names" {
        parse(attributeName, "foo") shouldEqual "foo"
        parse(attributeName, "foo_bar") shouldEqual "foo_bar"
        parse(attributeName, "foo123") shouldEqual "foo123"
    }

    "string constant" {
        parse(attribute, "foo = \"bar\"") shouldEqual Attribute("foo", Term.String<String>("bar"))
    }

    "number constant" {
        parse(attribute, "foo = 123") shouldEqual Attribute("foo", Term.Number<String>(123))
    }
})

class DecisionParserSpec : StringSpec({
    "decisions" {
        parse(decision, "permit") shouldEqual Decision.Permit
        parse(decision, "deny") shouldEqual Decision.Deny
    }
})

class ConditionParserSpec : StringSpec({
    "equal" {
        parse(condition, "foo = bar") shouldEqual Condition.Equal(a("foo"), a("bar"))
    }

    "greater" {
        parse(condition, "foo > \"bar\"") shouldEqual Condition.Greater(a("foo"), s("bar"))
    }

    "not equal" {
        parse(condition, "99 != 99") shouldEqual Condition.Not(Condition.Equal(n(99), n(99)))
    }

    "less" {
        parse(condition, "foo < bar") shouldEqual Condition.Greater(a("bar"), a("foo"))
    }

    "not" {
        parse(condition, "not a = b") shouldEqual Condition.Not(Condition.Equal(a("a"), a("b")))
    }

    "and" {
        parse(condition, "foo = 1 and bar = 2") shouldEqual Condition.And(
                Condition.Equal(a("foo"), n(1)),
                Condition.Equal(a("bar"), n(2))
        )
    }
})

class RuleParserSpec : StringSpec({
    "always permit" {
        parse(rule, "always permit") shouldEqual Rule.Always<String>(Decision.Permit)
    }

    "always deny" {
        parse(rule, "always deny") shouldEqual Rule.Always<String>(Decision.Deny)
    }

    "never" {
        parse(rule, "never") shouldEqual Rule.Never<String>()
    }

    "when" {
        parse(rule, "permit when abc = \"def\"") shouldEqual Rule.When(
                Condition.Equal(a("abc"), s("def")),
                Decision.Permit
        )
        parse(rule, "deny when 23 > 22") shouldEqual Rule.When(
                Condition.Greater(n(23), n(22)),
                Decision.Deny
        )
    }

    "one-leg branch" {
        parse(rule, "if \"abc\" = \"def\" always deny") shouldEqual Rule.Branch(
                Condition.Equal(s("abc"), s("def")),
                Rule.Always(Decision.Deny),
                Rule.Never()
        )
    }

    "two-leg branch" {
        parse(rule, "if 2 > 1 always permit else always deny") shouldEqual Rule.Branch(
                Condition.Greater(n(2), n(1)),
                Rule.Always(Decision.Permit),
                Rule.Always(Decision.Deny)
        )
    }

    "majority" {
        parse(rule, "majority permit { always permit, always deny }") shouldEqual Rule.Majority(
                Decision.Permit,
                listOf(Rule.Always(Decision.Permit), Rule.Always<String>(Decision.Deny))
        )
    }

    "any" {
        parse(rule, "any permit { always permit, always deny }") shouldEqual Rule.Any(
                Decision.Permit,
                listOf(Rule.Always(Decision.Permit), Rule.Always<String>(Decision.Deny))
        )
    }

    "all" {
        parse(rule, "all deny { always permit, always deny }") shouldEqual Rule.All(
                Decision.Deny,
                listOf(Rule.Always(Decision.Permit), Rule.Always<String>(Decision.Deny))
        )
    }

    "exclusive" {
        parse(rule, "exclusive { always permit, deny when a = b }") shouldEqual Rule.OneOf(
                listOf(Rule.Always(Decision.Permit),
                        Rule.When(Condition.Equal(Term.Attribute("a"), Term.Attribute("b")), Decision.Deny)
                )
        )
    }

    "complex rule" {
        parse(rule, """
            if foo = "bar"
              exclusive {
                permit when a > b,
                deny when a <= b
              }
            else
              always deny
        """) shouldEqual Rule.Branch(
                Condition.Equal(Term.Attribute("foo"), Term.String("bar")),
                Rule.OneOf(listOf(
                        Rule.When(Condition.Greater(Term.Attribute("a"), Term.Attribute("b")), Decision.Permit),
                        Rule.When(Condition.Or(
                                Condition.Equal(Term.Attribute("a"), Term.Attribute("b")),
                                Condition.Greater(Term.Attribute("b"), Term.Attribute("a"))
                        ), Decision.Deny)
                )),
                Rule.Always(Decision.Deny)
        )
    }
})

class RuleSetParserSpec : StringSpec({
    "ruleset" {
        val foo = Attribute("foo", Term.String<Attribute>("foo"))
        val bar = Attribute("bar", Term.Request<Attribute>("bar"))

        parse(ruleSet, """
            foo = "foo"
            bar = request "bar"

            any permit {
                permit when foo = bar,
                permit when foo = 123
            }

            """).resolve() shouldEqual RuleSet(
                // attributes
                listOf(foo, bar),

                // rules`
                listOf(Rule.Any(Decision.Permit,
                        listOf(
                                Rule.When(Condition.Equal(Term.Attribute(foo), Term.Attribute(bar)), Decision.Permit),
                                Rule.When(Condition.Equal(Term.Attribute(foo), Term.Number(123)), Decision.Permit)
                        ) as List<Rule<Attribute>>)
                ))
    }

})