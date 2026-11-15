; BLOAD MODULE FOR 32KB/49KB ROMS PART 1/2 (FOR CASLINK3 PROJECT)
; COPYRIGHT (C) 1999-2016 ALEXEY PODREZOV
; Modified by Roberto Focosi for MSX2Cas Project
; BLOAD MODULE FOR ROM'S FIRST BLOCKS

START:
    JP      START1

STARTA:
    .DW     00
ENDA:
    .DW     00
EXECA:
    .DW     00
CRC:
    .DB     00

LOADCMD:
    .DB     0x01E
    .str    "bload"
    .DB     34
    .str    "cas:"
    .DB     34
    .str    ",r"
    .DB     13,0

CASLSTR:
    .str    "< MSX2Cas > Loading, please wait...\0"

CASERR:
    .str    "< MSX2Cas > Fail: CRC ERROR!\0"

RAMERRMSG:
    .str    "< MSX2Cas > Fail: NO RAM!\0"

TST_A1:
    .DW     00
TST_A2:
    .DW     00
TST_CUR:
    .DW     00
SLT_ID:
    .DB     00
CUR_SLT:
    .DB     00
TMPVAL:
    .DB     00
SLOT_FND:
    .DB     00
UAP_PORT:
    .DB     00
UAP_PTR:
    .DW     00

START1:
    DI
    LD      HL,(STARTA)
    LD      DE,(ENDA)
    EX      DE,HL
    SCF
    CCF
    SBC     HL,DE
    PUSH    HL
    POP     BC
    LD      HL,#ROMCODE
    XOR     A
    PUSH    AF

START2:
    POP     AF
    ADD     A,(HL)
    INC     HL
    DEC     BC
    PUSH    AF
    LD      A,C
    OR      A
    JR      NZ,START2
    LD      A,B
    OR      A
    JR      NZ,START2
    POP     AF
    LD      B,A
    LD      HL,#CRC
    LD      A,(HL)
    CP      B
    JP      Z,CHKRAM

CRCERR:
    LD      DE,#CASERR
    JR      PRTERR

RAMERR:
    LD      DE,#RAMERRMSG

PRTERR:
    EI
    PUSH    DE
    CALL    0x006C        ; set screen 0
    LD      A,#0x0F
    LD      HL,#0x0F3E9
    LD      (HL),A
    LD      A,#8
    INC     HL
    LD      (HL),A
    INC     HL
    LD      (HL),A
    CALL    0x0062        ; set color 15,8,8
    XOR     A
    CALL    0x00C3        ; clear screen
    CALL    0x00CF        ; unhide functional keys
    LD      HL,#0x0101
    CALL    0x00C6        ; set cursor position to 1:1
    POP     DE

START3:
    LD      A,(DE)
    OR      A
    JR      Z,START4
    INC     DE
    CALL    0x00A2        ; display character
    INC     H
    CALL    0x00C6        ; set next position
    JR      START3

START4:
    LD      HL,#0x0103
    CALL    0x00C6        ; set cursor position to 1:3
    CALL    0x00C0        ; beep
    CALL    0x0156        ; clears keyboard buffer
    EI
    RET

FINDRAM:
    LD      (TST_A1),HL
    LD      (TST_A2),DE
    LD      B,#4

FR_PRI:
    LD      A,B
    DEC     A
    LD      (SLT_ID),A
    LD      E,A
    LD      D,#0
    LD      HL,#0x0FCC1   ; EXPTBL
    ADD     HL,DE
    LD      A,(HL)
    AND     #0x80
    JR      NZ,FR_EXP

    LD      A,(SLT_ID)
    PUSH    BC
    CALL    TSTSLOT
    POP     BC
    RET     NC
    JR      FR_NEXT

FR_EXP:
    LD      C,#0
FR_SUB:
    LD      A,C
    RLCA
    RLCA
    LD      HL,#SLT_ID
    OR      (HL)
    OR      #0x80
    PUSH    BC
    CALL    TSTSLOT
    POP     BC
    RET     NC
    INC     C
    LD      A,C
    CP      #4
    JR      NZ,FR_SUB

FR_NEXT:
    DJNZ    FR_PRI
    SCF
    RET

TSTSLOT:
    LD      (CUR_SLT),A
    LD      HL,(TST_A1)
    CALL    TSTADDR
    RET     C
    LD      HL,(TST_A2)
    CALL    TSTADDR
    RET     C
    LD      A,(CUR_SLT)
    RET

TSTADDR:
    LD      (TST_CUR),HL
    LD      A,(CUR_SLT)
    CALL    0x000C        ; RDSLT
    LD      (TMPVAL),A
    CPL
    LD      E,A
    LD      A,(CUR_SLT)
    LD      HL,(TST_CUR)
    CALL    0x0014        ; WRSLT
    LD      A,(CUR_SLT)
    LD      HL,(TST_CUR)
    CALL    0x000C        ; RDSLT
    PUSH    AF
    LD      A,(TMPVAL)
    LD      E,A
    LD      A,(CUR_SLT)
    LD      HL,(TST_CUR)
    CALL    0x0014        ; WRSLT restore
    POP     AF
    LD      HL,#TMPVAL
    XOR     (HL)
    CP      #0x0FF
    JR      Z,TA_OK
    SCF
    RET
TA_OK:
    OR      A
    RET

; Ensure target page (H = 0x00 or 0x40, A = mapper port 0xFC/0xFD, E = initial seg)
; does not alias any other active page via an uninitialized MSX Memory Mapper.
UNALIAS_PG:
    LD      (UAP_PORT),A
    LD      L,#0
    LD      (UAP_PTR),HL
    LD      D,#0
UAP_LOOP:
    CALL    CHK_PG_UNIQ
    RET     NC
    LD      A,(UAP_PORT)
    LD      C,A
    OUT     (C),E
    INC     E
    DEC     D
    JR      NZ,UAP_LOOP
    RET

CHK_PG_UNIQ:
    LD      A,(#0x0000)
    PUSH    AF
    LD      A,(#0x4000)
    PUSH    AF
    LD      A,(#0x8000)
    PUSH    AF
    LD      A,(#0xC000)
    PUSH    AF

    LD      HL,(UAP_PTR)
    LD      A,(HL)
    LD      B,A
    XOR     #0xFF
    LD      (HL),A
    CP      (HL)
    JR      NZ,CPU_FAIL

    POP     AF
    LD      C,A
    LD      A,(UAP_PTR+1)
    CP      #0xC0
    JR      Z,CPU_P2
    LD      A,(#0xC000)
    CP      C
    JR      NZ,CPU_FAIL_3
CPU_P2:
    POP     AF
    LD      C,A
    LD      A,(UAP_PTR+1)
    CP      #0x80
    JR      Z,CPU_P1
    LD      A,(#0x8000)
    CP      C
    JR      NZ,CPU_FAIL_2
CPU_P1:
    POP     AF
    LD      C,A
    LD      A,(UAP_PTR+1)
    CP      #0x40
    JR      Z,CPU_P0
    LD      A,(#0x4000)
    CP      C
    JR      NZ,CPU_FAIL_1
CPU_P0:
    POP     AF
    LD      C,A
    LD      A,(UAP_PTR+1)
    OR      A
    JR      Z,CPU_OK
    LD      A,(#0x0000)
    CP      C
    JR      NZ,CPU_FAIL_0
CPU_OK:
    LD      HL,(UAP_PTR)
    LD      (HL),B
    OR      A
    RET

CPU_FAIL:
    POP     AF
CPU_FAIL_3:
    POP     AF
CPU_FAIL_2:
    POP     AF
CPU_FAIL_1:
    POP     AF
CPU_FAIL_0:
    LD      HL,(UAP_PTR)
    LD      (HL),B
    SCF
    RET

CHKRAM:
    LD      A,(STARTA+1)
    CP      #0x80
    JR      NC,START5

    LD      HL,(STARTA)
    LD      DE,(ENDA)
    DEC     DE
    DEC     DE
    LD      A,H
    AND     #0x0C0
    LD      B,A
    LD      A,D
    AND     #0x0C0
    CP      B
    JR      Z,CR_PGOK
    LD      A,B
    OR      #0x3F
    LD      D,A
    LD      E,#0x0FE
CR_PGOK:
    CALL    FINDRAM
    JP      C,RAMERR
    LD      (SLOT_FND),A

START5:
    EI
    CALL    0x006C        ; set screen 0
    LD      A,#0x0F
    LD      HL,#0x0F3E9
    LD      (HL),A
    LD      A,#4
    INC     HL
    LD      (HL),A
    INC     HL
    LD      (HL),A
    CALL    0x0062        ; set color 15,4,4
    XOR     A
    CALL    0x00C3        ; clear screen
    CALL    0x00CC        ; hide functional keys
    LD      HL,#0x0101
    CALL    0x00C6        ; set cursor position to 1:1
    LD      DE,#CASLSTR

START6:
    LD      A,(DE)
    OR      A
    JR      Z,START7
    INC     DE
    CALL    0x00A2        ; display character
    INC     H
    CALL    0x00C6        ; set next position
    JR      START6

START7:
    LD      HL,#0x0103
    CALL    0x00C6        ; set cursor position to 1:3
    CALL    0x0156        ; clears keyboard buffer
    DI
    LD      HL,#0x0FBF0
    LD      (0x0F3F8),HL
    LD      (0x0F3FA),HL
    LD      HL,#LOADCMD
    LD      DE,#0x0FBF0
    LD      BC,#29
    LDIR                  ; send command to buffer
    LD      HL,#0x0FBF0+29
    LD      (0x0F3F8),HL

LOADROM:
    DI
    LD      A,(STARTA+1)
    CP      #0x40
    JR      C,LR_PG0

    ; Page 1 (0x4000-0x7FFF)
    LD      A,(SLOT_FND)
    LD      H,#0x40
    CALL    0x0024        ; ENASLT: map Page 1 to RAM
    DI
    LD      H,#0x40
    LD      A,#0xFD
    LD      E,#2
    CALL    UNALIAS_PG
    LD      HL,(#ENDA)
    LD      DE,(#STARTA)
    OR      A
    SBC     HL,DE
    LD      B,H
    LD      C,L
    LD      HL,#ROMCODE
    LDIR
    LD      A,(0x0FCC1)   ; EXPTBL[0]: Main BIOS/BASIC ROM slot
    LD      H,#0x40
    CALL    0x0024        ; ENASLT: restore Page 1 to BASIC
    EI
    RET

LR_PG0:
    IN      A,(0x0A8)
    PUSH    AF
    LD      A,(SLOT_FND)
    BIT     7,A
    JR      Z,LR_P0_PRI

    LD      B,A
    AND     #0x03
    LD      E,A
    LD      D,#0
    LD      HL,#0x0FCC5   ; SLTTBL
    ADD     HL,DE
    LD      A,(HL)
    PUSH    AF            ; Save old SLTTBL[P]
    LD      A,B
    RRCA
    RRCA
    AND     #0x03
    LD      B,A
    LD      A,(HL)
    AND     #0x0FC
    OR      B
    LD      C,A           ; C = temp secondary slot byte

    LD      A,E
    RRCA
    RRCA
    AND     #0x0C0
    LD      B,A

    IN      A,(0x0A8)
    LD      D,A
    AND     #0x3F
    OR      B
    OUT     (0x0A8),A
    LD      A,C
    LD      (0x0FFFF),A
    LD      A,D
    OUT     (0x0A8),A

LR_P0_PRI:
    LD      A,(SLOT_FND)
    AND     #0x03
    LD      B,A
    IN      A,(0x0A8)
    AND     #0x0FC
    OR      B
    OUT     (0x0A8),A

    LD      H,#0x00
    LD      A,#0xFC
    LD      E,#3
    CALL    UNALIAS_PG

    LD      HL,(#ENDA)
    LD      DE,(#STARTA)
    OR      A
    SBC     HL,DE
    LD      B,H
    LD      C,L
    LD      HL,#ROMCODE
    LDIR

    LD      A,(SLOT_FND)
    BIT     7,A
    JR      Z,LR_P0_DONE

    AND     #0x03
    RRCA
    RRCA
    AND     #0x0C0
    LD      B,A
    POP     AF            ; Restore old SLTTBL[P] into C
    LD      C,A
    IN      A,(0x0A8)
    LD      D,A
    AND     #0x3F
    OR      B
    OUT     (0x0A8),A
    LD      A,C
    LD      (0x0FFFF),A
    LD      A,D
    OUT     (0x0A8),A

LR_P0_DONE:
    POP     AF
    OUT     (0x0A8),A
    EI
    RET
    NOP

ROMCODE:
