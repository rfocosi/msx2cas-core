; BLOAD MODULE FOR 8/16KB ROMS WITH RESET (FOR CASLINK3 PROJECT)
; COPYRIGHT (C) 1999-2016 ALEXEY PODREZOV
; Modified by Roberto Focosi for MSX2Cas Project

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
SLOT0_FND:
    .DB     00

START1:
    DI
    LD      HL,(STARTA)
    LD      DE,(#ENDA)
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
    JP      Z,START5

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

START5:
    DI
    ; Find Page 1 RAM (needed for 0x4000-0x7FFF ROM or fake AB header at 0x4000)
    LD      A,(STARTA+1)
    CP      #0x40
    JR      C,S5_P1_HDR
    CP      #0x80
    JR      NC,S5_P1_HDR

    LD      HL,(STARTA)
    LD      DE,(ENDA)
    DEC     DE
    DEC     DE
    JR      S5_CHK_P1

S5_P1_HDR:
    LD      HL,#0x4000
    LD      DE,#0x4003

S5_CHK_P1:
    CALL    FINDRAM
    JP      C,RAMERR
    LD      (SLOT_FND),A

    ; If STARTA < 0x4000, also find Page 0 RAM
    LD      A,(STARTA+1)
    CP      #0x40
    JR      NC,S5_MAP_P1

    LD      HL,(STARTA)
    LD      DE,(ENDA)
    DEC     DE
    DEC     DE
    CALL    FINDRAM
    JP      C,RAMERR
    LD      (SLOT0_FND),A

S5_MAP_P1:
    LD      A,(SLOT_FND)
    LD      H,#0x40
    CALL    0x0024        ; ENASLT: map Page 1 to detected RAM slot
    DI

START6:
    LD      HL,(#STARTA)
    LD      A,H
    CP      #0x40
    JR      C,START7
    CP      #0x80
    JR      C,START8

START7:
    LD      HL,#0x4000
    LD      A,#0x41
    LD      (HL),A
    INC     A
    INC     HL
    LD      (HL),A        ; set AB at 0x4000
    INC     HL
    LD      DE,(EXECA)
    LD      (HL),E
    INC     HL
    LD      (HL),D        ; set start address at 0x4002

    LD      A,(STARTA+1)
    CP      #0x40
    JR      C,LOAD_PG0

    LD      HL,#START8
    LD      DE,#LOAD_PG0
    EX      DE,HL
    OR      A
    SBC     HL,DE
    LD      B,H
    LD      C,L
    LD      HL,#START8
    LD      DE,#0x0F560
    PUSH    DE
    LDIR
    RET

START8:
    LD      HL,(#ENDA)
    LD      DE,(#STARTA)
    OR      A
    SBC     HL,DE
    LD      B,H
    LD      C,L
    LD      HL,#ROMCODE
    LDIR
    RST     0x30
    .DW     0
    .DW     0

LOAD_PG0:
    IN      A,(0x0A8)
    PUSH    AF
    LD      A,(SLOT0_FND)
    BIT     7,A
    JR      Z,PG0_PRI

    LD      B,A
    AND     #0x03
    LD      E,A
    LD      D,#0
    LD      HL,#0x0FCC5   ; SLTTBL
    ADD     HL,DE
    LD      A,B
    RRCA
    RRCA
    AND     #0x03
    LD      B,A
    LD      A,(HL)
    AND     #0x0FC
    OR      B
    LD      (HL),A
    LD      C,A

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

PG0_PRI:
    LD      A,(SLOT0_FND)
    AND     #0x03
    LD      B,A
    IN      A,(0x0A8)
    AND     #0x0FC
    OR      B
    OUT     (0x0A8),A

    LD      HL,(#ENDA)
    LD      DE,(#STARTA)
    OR      A
    SBC     HL,DE
    LD      B,H
    LD      C,L
    LD      HL,#ROMCODE
    LDIR
    POP     AF
    OUT     (0x0A8),A
    RST     0x30
    .DW     0
    .DW     0
    NOP

ROMCODE:
