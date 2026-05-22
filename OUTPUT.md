(base) eliot@pop-os vm/test (main) » shelltest -D VM=../c/bin/vm .
:./bignums.test: [Failed]
Command:
../c/bin/vm ./bignums.l3a
Expected stdout:
Factorial of? 1000! = 4023872600770937735437024339230039857193748642107146325437999104299385123986290205920442084869694048004799886101971960586316668729948085589013238296699445909974245040870737599188236277271887325197795059509952761208749754624970436014182780946464962910563938874378864873371191810458257836478499770124766328898359557354325131853239584630755574091142624174743493475534286465766116677973966688202912073791438537195882498081268678383745597317461360853795345242215865932019280908782973...(2091 more)
Got stdout:
Expected stderr:

Got stderr:
Segmentation fault (core dumped)
Expected exit code:
"0"
Got exit code:
139
:./expr-and.test: [OK]
:./expr-begin.test: [Failed]
Command:
../c/bin/vm ./expr-begin.l3a; echo
Expected stdout:
CABC

Got stdout:

Expected stderr:

Got stderr:
Segmentation fault (core dumped)

:./expr-cond.test: [OK]
:./expr-fun.test: [OK]
:./expr-if.test: [OK]
:./expr-let.test: [OK]
:./expr-letrec.test: [Failed]
Command:
../c/bin/vm ./expr-letrec.l3a; echo
Expected stdout:
BAB

Got stdout:

Expected stderr:

Got stderr:
Segmentation fault (core dumped)

:./expr-lets.test: [OK]
:./expr-not.test: [OK]
:./expr-or.test: [Failed]
Command:
../c/bin/vm ./expr-or.l3a; echo
Expected stdout:
FABCDEF

Got stdout:

Expected stderr:

Got stderr:
Segmentation fault (core dumped)

:./expr-rec.test: [OK]
:./maze.test: [OK]
:./prim-add.test: [OK]
:./prim-and.test: [OK]
:./prim-block-alloc.test: [Failed]
Command:
../c/bin/vm ./prim-block-alloc.l3a; echo
Expected stdout:
BAB

Got stdout:

Expected stderr:

Got stderr:
Segmentation fault (core dumped)

:./prim-block-get-set.test: [OK]
:./prim-block-length.test: [OK]
:./prim-block-tag.test: [OK]
:./prim-blockp.test: [OK]
:./prim-boolp.test: [Failed]
Command:
../c/bin/vm ./prim-boolp.l3a; echo
Expected stdout:
EABCDE

Got stdout:

Expected stderr:

Got stderr:
Segmentation fault (core dumped)

:./prim-char-to-int.test: [OK]
:./prim-charp.test: [OK]
:./prim-div.test: [OK]
:./prim-eq.test: [OK]
:./prim-int-to-char.test: [OK]
:./prim-intp.test: [OK]
:./prim-le.test: [OK]
:./prim-lt.test: [OK]
:./prim-mod.test: [OK]
:./prim-mul.test: [OK]
:./prim-or.test: [OK]
:./prim-shift-left.test: [OK]
:./prim-shift-right.test: [Failed]
Command:
../c/bin/vm ./prim-shift-right.l3a; echo
Expected stdout:
CABC

Got stdout:

Expected stderr:

Got stderr:
Segmentation fault (core dumped)

:./prim-sub.test: [OK]
:./prim-unitp.test: [OK]
:./prim-xor.test: [OK]
:./queens.test: [OK]
:./stmt-def.test: [Failed]
Command:
../c/bin/vm ./stmt-def.l3a; echo
Expected stdout:
CABC

Got stdout:

Expected stderr:

Got stderr:
Segmentation fault (core dumped)

:./stmt-defrec.test: [OK]
:./stmt-halt.test: [OK]
:./sudoku.test: [Failed]
Command:
../c/bin/vm ./sudoku.l3a
Expected stdout:
2-sudoku
+-----+-----+
| 1 2 | 3 4 |
| 3 4 | 1 2 |
+-----+-----+
| 2 1 | 4 3 |
| 4 3 | 2 1 |
+-----+-----+

3-sudoku
+-------+-------+-------+
| 1 2 3 | 4 5 6 | 7 8 9 |
| 4 5 6 | 7 8 9 | 1 2 3 |
| 7 8 9 | 1 2 3 | 4 5 6 |
+-------+-------+-------+
| 2 1 4 | 3 6 5 | 8 9 7 |
| 3 6 5 | 8 9 7 | 2 1 4 |
| 8 9 7 | 2 1 4 | 3 6 5 |
+-------+-------+-------+
| 5 3 1 | 6 4 2 | 9 7 8 |
| 6 4 2 | 9 7 8 | 5 3 1 |
| 9 7 8 | 5 3 1 | 6 4 2 |
+-------+-------+-------+

3-sudoku
+-------+-------+-------+
| 1 9 2 |...(304 more)
Got stdout:
Expected stderr:

Got stderr:
Segmentation fault (core dumped)

:./unimaze.test: [Failed]
Command:
../c/bin/vm -m 2000000 ./unimaze.l3a
Expected stdout:
 Maze width: Maze height: Random seed: ┌┬───┬───┬──┬┬─┬┬──┬───┬────┬─┬┬──┬────────┬─────┬┬┬──┬┬┬──┬┬─┬─┬─────┬┬────┬─┐
││╷┌╴╵╷╶┐╵╶─┘│╶┘└┐╷╵╶┬╴└─╴╶┬┘╷││┌┐│┌╴┌─╴┌╴╶┼─┬┐╶─┤╵└┐╷││╵╷╶┘│┌┼┐└┬╴╷╶─┘├┐╷╶┐├╴│
│├┴┼──┴┐├─┬┬╴│╶─┐├┴─┬┘╶┐╶┐╶┼╴└┘└┤╵├┴╴└┐┌┤┌╴└┐╵│┌┬┴╴╷╵│╵│┌┘╷┌┤╵╵├┐╵┌┴┐╷╶┤╵└┐├┴╴│
│└┐╵┌╴╶┴┘╶┤├╴└╴╷└┴┬┐│┌┬┘╷├┬┤╷╷╶─┴┐│┌─┐└┤╵│╶┐╵╶┤│└─╴└┐├┬┼┘╷├┘└─╴╵└─┘╷││╶┴┐┌┘╵┌─┤
├╴│┌┤╶┬╴╶┐╵└──┬┤╶┐╵│╵│╵╶┴┤│└┼┴┬┬╴╵└┘┌┘╶┼─┴╴│╶┬┤├╴╶┬─┴┘╵│┌┴┘╶┬┐╷╷┌╴╶┼┘└┐╶┴┤┌╴├┐│
│╷││╵┌┴╴╷│╶┐╷╷│╵╶┼╴├╴└┐┌╴│├╴└╴│└─╴╷╷└┬┐└─┬╴│╷╵│└─┐└╴╶┐╷╵├───┘...(2099 more)
Got stdout:
Expected stderr:

Got stderr:
Segmentation fault (core dumped)
Expected exit code:
"0"
Got exit code:
139

         Test Cases   Total       
 Passed  33           33
 Failed  10           10
 Total   43           43
