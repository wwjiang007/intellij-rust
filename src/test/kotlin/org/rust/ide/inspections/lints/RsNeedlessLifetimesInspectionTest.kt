/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import org.intellij.lang.annotations.Language
import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.ide.inspections.RsInspectionsTestBase

class RsNeedlessLifetimesInspectionTest : RsInspectionsTestBase(RsNeedlessLifetimesInspection::class) {

    fun `test no output lifetimes 1`() = doTest("""
        <weak_warning>fn <caret>foo<'a>(s: &'a str)</weak_warning> { unimplemented!() }
    """, """
        fn <caret>foo(s: &str) { unimplemented!() }
    """)

    fun `test no output lifetimes 2`() = doTest("""
        <weak_warning>fn <caret>foo<'a>(i: i32, s: &'a str)</weak_warning> { unimplemented!() }
    """, """
        fn <caret>foo(i: i32, s: &str) { unimplemented!() }
    """)

    fun `test one input lifetime 1`() = doTest("""
        <weak_warning>fn <caret>foo<'a>(s: &'a str, i: i32) -> &'a str</weak_warning> { unimplemented!() }
    """, """
        fn <caret>foo(s: &str, i: i32) -> &str { unimplemented!() }
    """)

    fun `test one input lifetime 2`() = doTest("""
        struct S<'a>(&'a str);
        <weak_warning>fn <caret>foo<'a>(x: &'a str) -> S<'a></weak_warning> { unimplemented!() }
    """, """
        struct S<'a>(&'a str);
        fn <caret>foo(x: &str) -> S { unimplemented!() }
    """)

    fun `test one input lifetime 3`() = doTest("""
        <weak_warning>fn <caret>foo<'a>(x: &'a str) -> Box<&'a str></weak_warning> { unimplemented!() }
    """, """
        fn <caret>foo(x: &str) -> Box<&str> { unimplemented!() }
    """)

    fun `test one input lifetime (fn pointer with parameter lifetime)`() = doTest("""
        /*weak_warning*/fn /*caret*/foo<'a>(s: &'a str, b: fn(&'a i32)) -> &'a i32/*weak_warning**/ { unimplemented!() }
    """, """
        fn foo(s: &str, b: fn(&i32)) -> &i32 { unimplemented!() }
    """)

    fun `test one input lifetime (Fn trait with parameter lifetime)`() = doTest("""
        /*weak_warning*/fn /*caret*/foo<'a>(s: &'a str, b: impl Fn(&'a i32)) -> &'a i32/*weak_warning**/ { unimplemented!() }
    """, """
        fn foo(s: &str, b: impl Fn(&i32)) -> &i32 { unimplemented!() }
    """)

    fun `test one input lifetime (fn pointer with path lifetime)`() = doTest("""
        trait Trait {
            type AssocType<'a>;
            fn foo<'a>(_: &'a str, _: fn(Self::AssocType<'a>)) -> &'a i32 { unimplemented!() }
        }
    """)

    fun `test one input lifetime (Fn trait with path lifetime)`() = doTest("""
        trait Trait {
            type AssocType<'a>;
            fn foo<'a>(_: &'a str, _: impl Fn(Self::AssocType<'a>)) -> &'a i32 { unimplemented!() }
        }
    """)

    fun `test no input lifetimes`() = doTest("""
        fn foo() -> &str { unimplemented!() }
    """)

    fun `test assoc param`() = doTest("""
        <weak_warning>fn <caret>foo<'a>(x: &'a str) -> Box<Iterator<Item=&'a str>></weak_warning> { unimplemented!() }
    """, """
        fn <caret>foo(x: &str) -> Box<Iterator<Item=&str>> { unimplemented!() }
    """)

    fun `test all params and arguments`() = doTest("""
        trait A<'a, T, const N: usize> { type Item; }
        <weak_warning>fn <caret>foo<'a, T, const N: usize>(x: &'a str) -> Box<A<'a, T, { N + 1 }, Item=i32>></weak_warning> { unimplemented!() }
    """, """
        trait A<'a, T, const N: usize> { type Item; }
        fn <caret>foo<T, const N: usize>(x: &str) -> Box<A<T, { N + 1 }, Item=i32>> { unimplemented!() }
    """)

    fun `test check struct, enum, trait, alias`() = checkByText("""
        struct S<'a>;
        enum E<'a> {}
        trait T<'a> {}
        type A<'a> = S<'a>;

        fn foo1<'a>(d: &'a S) -> &'a () { unimplemented!() }
        fn foo2<'a>(d: &'a E) -> &'a () { unimplemented!() }
        fn foo3<'a>(d: &'a T) -> &'a () { unimplemented!() }
        fn foo4<'a>(d: &'a A) -> &'a () { unimplemented!() }
    """, checkWeakWarn = true)

    fun `test two input lifetimes no output lifetimes 1`() = doTest("""
        <weak_warning>fn <caret>foo<'a>(a: &'a str, b: &str)</weak_warning> { unimplemented!() }
    """, """
        fn foo(a: &str, b: &str) { unimplemented!() }
    """)

    fun `test two input lifetimes no output lifetimes 2`() = doTest("""
        struct S<'s>(&'s str);
        fn foo<'a>(s: &'a str, t: S<'a>) { unimplemented!() }
    """)

    fun `test two input lifetimes one output lifetime`() = doTest("""
        fn foo<'a>(s: &'a str, t: &str) -> &str { unimplemented!() }
    """)

    fun `test output trait type`() = doTest("""
        trait T<'a> {}
        impl <'a> T<'a> for () {}
        <weak_warning>fn <caret>foo<'a>(a: &'a str) -> impl T<'a></weak_warning> { unimplemented!() }
    """, """
        trait T<'a> {}
        impl <'a> T<'a> for () {}
        fn <caret>foo(a: &str) -> impl T { unimplemented!() }
    """)

    fun `test input struct with implicit lifetime`() = doTest("""
        struct S<'s>(&'s str);
        <weak_warning>fn <caret>foo<'a>(s: &'a str, t: S)</weak_warning> { unimplemented!() }
    """, """
        struct S<'s>(&'s str);
        fn <caret>foo(s: &str, t: S) { unimplemented!() }
    """)

    fun `test input struct with type argument and implicit lifetime`() = doTest("""
        struct S<'s, T: 's>(&'s T);
        <weak_warning>fn <caret>foo<'a>(s: &'a S<i32>)</weak_warning> { unimplemented!(); }
    """, """
        struct S<'s, T: 's>(&'s T);
        fn <caret>foo(s: &S<i32>) { unimplemented!(); }
    """)

    fun `test self 1`() = doTest("""
        struct S;
        impl S {
            <weak_warning>fn <caret>foo<'a>(&'a mut self) -> &'a mut S</weak_warning> { unimplemented!() }
        }
    """, """
        struct S;
        impl S {
            fn <caret>foo(&mut self) -> &mut S { unimplemented!() }
        }
    """)

    fun `test self 2`() = doTest("""
        struct S;
        impl S {
            <weak_warning>fn <caret>foo<'a, 'b>(&'a self, x: &'b str) -> &'a str</weak_warning> { unimplemented!() }
        }
    """, """
        struct S;
        impl S {
            fn <caret>foo(&self, x: &str) -> &str { unimplemented!() }
        }
    """)

    fun `test self 3`() = doTest("""
        struct S;
        impl S {
            <weak_warning>fn <caret>foo<'a, 'b>(&'a self, b: &'b str) -> &'a str</weak_warning> { unimplemented!() }
        }
    """, """
        struct S;
        impl S {
            fn <caret>foo(&self, b: &str) -> &str { unimplemented!() }
        }
    """)

    fun `test self 4`() = doTest("""
        struct S;
        impl S {
            <weak_warning>fn <caret>foo<'a, 'b, 'c>(&'a self, b: &'b str, c: &'c str) -> &'a str</weak_warning> { unimplemented!() }
        }
    """, """
        struct S;
        impl S {
            fn <caret>foo(&self, b: &str, c: &str) -> &str { unimplemented!() }
        }
    """)

    fun `test self 5`() = doTest("""
        struct S;
        impl S {
            <weak_warning>fn <caret>foo<'a, 'b, 'c>(&'a self, b: &'b str, c: &'c str) -> &str</weak_warning> { unimplemented!() }
        }
    """, """
        struct S;
        impl S {
            fn <caret>foo(&self, b: &str, c: &str) -> &str { unimplemented!() }
        }
    """)

    fun `test self 6`() = doTest("""
        struct S;
        impl S {
            fn foo<'b, 'c>(self, b: &'b str, c: &'c str) -> &str { unimplemented!() }
        }
    """)

    fun `test self 7 (&Self)`() = doTest("""
        struct S;
        impl S {
            /*weak_warning*/fn /*caret*/foo<'a, 'b>(self: &'a Self, _: &'b i32) -> &'a i32/*weak_warning**/ { &0 }
        }
    """, """
        struct S;
        impl S {
            fn foo(self: &Self, _: &i32) -> &i32 { &0 }
        }
    """)

    fun `test self 8 (Box)`() = doTest("""
        struct S;
        impl S {
            /*weak_warning*/fn /*caret*/foo<'a, 'b>(self: Box<&'a Self>, _: &'b i32) -> &'a i32/*weak_warning**/ { &0 }
        }
    """, """
        struct S;
        impl S {
            fn foo(self: Box<&Self>, _: &i32) -> &i32 { &0 }
        }
    """)

    fun `test inside impl`() = doTest("""
        struct S<'a>(&'a str);
        <weak_warning>fn <caret>foo<'a>(x: &'a mut [u8]) -> S<'a></weak_warning> { unimplemented!() }
    """, """
        struct S<'a>(&'a str);
        fn <caret>foo(x: &mut [u8]) -> S { unimplemented!() }
    """)

    fun `test inside trait`() = doTest("""
        trait T<'a> {
            fn foo(&'a self) -> &'a str { unimplemented!() }
            <weak_warning>fn <caret>bar<'b>(&'b self) -> &'b str</weak_warning> { unimplemented!() }
        }
    """, """
        trait T<'a> {
            fn foo(&'a self) -> &'a str { unimplemented!() }
            fn <caret>bar(&self) -> &str { unimplemented!() }
        }
    """)

    fun `test inside impl trait`() = doTest("""
        trait T<'a> {
            fn foo(&'a self) -> &'a str;
            fn bar(&self) -> &str;
        }
        impl<'a> T<'a> for &'a str {
            fn foo(&'a self) -> &'a str { unimplemented!() }
            <weak_warning>fn <caret>bar<'b>(&'b self) -> &'b str</weak_warning> { unimplemented!() }
        }
    """, """
        trait T<'a> {
            fn foo(&'a self) -> &'a str;
            fn bar(&self) -> &str;
        }
        impl<'a> T<'a> for &'a str {
            fn foo(&'a self) -> &'a str { unimplemented!() }
            fn <caret>bar(&self) -> &str { unimplemented!() }
        }
    """)

    fun `test no refs only structs 1`() = doTest("""
        struct S<'s>(&'s str);
        <weak_warning>fn <caret>foo<'a>(t: S<'a>) -> S<'a></weak_warning> { unimplemented!() }
    """, """
        struct S<'s>(&'s str);
        fn <caret>foo(t: S) -> S { unimplemented!() }
    """)

    fun `test no refs only structs 2`() = doTest("""
        struct S<'s, T: 's>(&'s T);
        <weak_warning>fn <caret>foo<'a>(t: S<'a, i32>) -> S<'a, i32></weak_warning> { unimplemented!() }
    """, """
        struct S<'s, T: 's>(&'s T);
        fn <caret>foo(t: S<i32>) -> S<i32> { unimplemented!() }
    """)

    fun `test no elision for struct and impl`() = doTest("""
        struct S<'a>(&'a str);
        trait T {}
        impl<'a> T for S<'a> {}
    """)

    fun `test no elision input struct with lifetime`() = doTest("""
        struct S<'s>(&'s str);
        fn foo<'a>(s: &'a str, t: S<'a>) { unimplemented!() }
    """)

    fun `test no elision when lifetime in bound 1`() = doTest("""
        struct S<'s>(&'s str);
        fn foo<'a>(s: &'a str, t: S<'a>) { unimplemented!() }
    """)

    fun `test no elision when lifetime in bound 2`() = doTest("""
        trait S<'s> {}
        fn foo<'a, T: S<'a>>(s: &'a str, t: T) { unimplemented!() }
    """)

    fun `test no elision when lifetime in where 1`() = doTest("""
        fn foo<'a, 'b>(s: &'a str, t: &'b str) where 'b: 'a { unimplemented!() }
    """)

    fun `test no elision when lifetime in where 2`() = doTest("""
        trait S<'s> {}
        fn foo<'a, T>(s: &'a str, t: T) where T: S<'a> { unimplemented!() }
    """)

    // https://github.com/intellij-rust/intellij-rust/issues/7092
    fun `test no elision when lifetime in where 3`() = doTest("""
        trait A<'b> {}

        struct C<T>(T);

        impl<T> C<T> {
            fn d<'e>(&'e self) where T: A<'e> {}
        }
    """)

    fun `test no elision when lifetime in body`() = doTest("""
        fn foo<'a>(s: &'a str) { let x: &'a str = unimplemented!(); }
    """)

    fun `test no elision for 'static`() = doTest("""
        fn foo(s: &'static str) { unimplemented!(); }
    """)

    fun `test no elision for for`() = doTest("""
        fn foo(x: for<'a> fn(&'a str)) { unimplemented!(); }
    """)

    fun `test no elision for bounded LT parameters`() = doTest("""
        fn foo<'a, 'b: 'a>(a: &'a str, b: &'b str) { unimplemented!() }
    """)

    fun `test no elision if there are lifetime bounds in trait type`() = doTest("""
        fn foo<'a>(a: &'a [i32]) -> Box<Iterator<Item=i32> + 'a> { unimplemented!() }
    """)

    fun `test ignore 'static in body`() = doTest("""
        <weak_warning>fn <caret>foo<'a>(s: &'a str)</weak_warning> { let x: &'static str = unimplemented!(); }
    """, """
        fn <caret>foo(s: &str) { let x: &'static str = unimplemented!(); }
    """)

    fun `test ignore items in body`() = doTest("""
        <weak_warning>fn <caret>foo<'a>(s: &'a str)</weak_warning> {
            fn bar<'b>(s: &'b str, t: &str) -> &str { unimplemented!(); }
        }
    """, """
        fn <caret>foo(s: &str) {
            fn bar<'b>(s: &'b str, t: &str) -> &str { unimplemented!(); }
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test no elision for impl 1`() = doTest("""
        fn foo<'a>(_: impl Into<&'a ()>) {}
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test no elision for impl 2`() = doTest("""
        struct S<'a>(&'a str);
        fn _foo<'a>(_: impl Into<S<'a>>) {}
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test no elision for impl 3`() = doTest("""
        fn foo<'a>(_: impl Iterator<Item = &'a ()>) {}
    """)

    fun `test allow`() = checkByText("""
        <weak_warning>fn foo1<'a>(_: &'a str)</weak_warning> {}

        #[allow(clippy::needless_lifetimes)]
        fn foo2<'a>(_: &'a str) {}

        #[allow(clippy::complexity)]
        fn foo3<'a>(_: &'a str) {}

        #[allow(clippy::all)]
        fn foo4<'a>(_: &'a str) {}

        #[allow(clippy)]
        fn foo5<'a>(_: &'a str) {}
    """, checkWeakWarn = true)

    fun `test global allow`() = checkByText("""
        #![allow(clippy::needless_lifetimes)]

        fn foo1<'a>(_: &'a str) {}

        fn foo2<'a>(_: &'a str) {}
    """, checkWeakWarn = true)

    fun `test async fn`() = checkByText("""
        struct S<'a>(&'a str);

        async fn foo1<'a>(a: S<'a>) -> S<'a> {}

        async <weak_warning>fn foo2<'a>(a: &'a str) -> &'a str</weak_warning> {}
    """, checkWeakWarn = true)

    private fun doTest(
        @Language("Rust") text: String
    ) = checkFixIsUnavailable(FIX_NAME, text, checkWeakWarn = true)

    private fun doTest(
        @Language("Rust") before: String,
        @Language("Rust") after: String
    ) = checkFixByText(FIX_NAME, before, after, checkWeakWarn = true)

    companion object {
        private const val FIX_NAME: String = "Elide lifetimes"
    }
}
