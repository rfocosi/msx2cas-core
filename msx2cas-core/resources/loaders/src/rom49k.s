; BLOAD MODULE FOR 49KB ROMS PART 3 (FOR CASLINK3 PROJECT)
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
SLOT1_FND:
    .DB     00
SLOT0_FND:
    .DB     00

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
    ; Probe Page 0 and Page 1 RAM
    LD      HL,#0x0000
    LD      DE,#0x3FFE
    CALL    FINDRAM
    JP      C,RAMERR
    LD      (SLOT0_FND),A

    LD      HL,#0x4000
    LD      DE,#0x7FFE
    CALL    FINDRAM
    JP      C,RAMERR
    LD      (SLOT1_FND),A

    ; Map Page 1 to detected RAM slot via ENASLT
    LD      H,#0x40
    CALL    0x0024
    DI
    CALL    GUARD_SPLIT_P2

    ; Pre-configure Page 0 secondary slot only when executing in Page 0 (EXECA < 0x4000),
    ; keeping BIOS mapped in Page 0 otherwise (needed when Page 0 RAM shares slot 0)
    LD      A,(EXECA+1)
    CP      #0x40
    JR      NC,S5_STUB
    LD      A,(SLOT0_FND)
    BIT     7,A
    JR      Z,S5_STUB
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

S5_STUB:
    LD      HL,#START6
    LD      DE,#ROMCODE
    EX      DE,HL
    OR      A
    SBC     HL,DE
    LD      B,H
    LD      C,L
    LD      HL,#START6
    LD      DE,#0x0F560
    PUSH    DE
    LDIR
    RET

; On split-RAM machines where Page 1 RAM lacks Page 2 RAM, a 49K ROM with Block 3 at 0x8000
; will break itself if startup calls BIOS ENASLT(H=0x80). NOP out that Page-2 ENASLT call.
GUARD_SPLIT_P2:
    LD      A,(STARTA+1)
    CP      #0x80
    RET     NZ
    LD      HL,#0x8000
    LD      (TST_A1),HL
    LD      HL,#0xBFFE
    LD      (TST_A2),HL
    LD      A,(SLOT1_FND)
    CALL    TSTSLOT
    DI
    RET     NC

    LD      HL,(EXECA)
    LD      B,#2
GSP_JP:
    LD      A,H
    CP      #0x40
    RET     C
    CP      #0x80
    RET     NC
    LD      A,(HL)
    CP      #0xC3
    JR      NZ,GSP_SCAN_INIT
    INC     HL
    LD      E,(HL)
    INC     HL
    LD      D,(HL)
    EX      DE,HL
    DJNZ    GSP_JP

GSP_SCAN_INIT:
    LD      A,H
    CP      #0x40
    RET     C
    CP      #0x80
    RET     NC
    LD      B,#0x60
    LD      C,#0

GSP_LOOP:
    LD      A,H
    CP      #0x80
    RET     NC
    LD      A,(HL)
    CP      #0x26
    JR      NZ,GSP_CHK_HL
    PUSH    HL
    INC     HL
    LD      A,(HL)
    POP     HL
    CP      #0x80
    JR      NZ,GSP_CLR_WIN
    LD      C,#11
    JR      GSP_NEXT
GSP_CLR_WIN:
    LD      C,#0
    JR      GSP_NEXT

GSP_CHK_HL:
    CP      #0x21
    JR      NZ,GSP_CHK_CALL
    PUSH    HL
    INC     HL
    INC     HL
    LD      A,(HL)
    POP     HL
    CP      #0x80
    JR      NZ,GSP_NEXT
    LD      C,#12
    JR      GSP_NEXT

GSP_CHK_CALL:
    CP      #0xCD
    JR      NZ,GSP_NEXT
    LD      A,C
    OR      A
    JR      Z,GSP_NEXT
    PUSH    HL
    INC     HL
    LD      A,(HL)
    CP      #0x24
    JR      NZ,GSP_NO_MATCH
    INC     HL
    LD      A,(HL)
    OR      A
    JR      NZ,GSP_NO_MATCH
    POP     HL
    XOR     A
    LD      (HL),A
    INC     HL
    LD      (HL),A
    INC     HL
    LD      (HL),A
    JR      GSP_CHK_PG0

GSP_NO_MATCH:
    POP     HL

GSP_NEXT:
    LD      A,C
    OR      A
    JR      Z,GSP_ADV
    DEC     C
GSP_ADV:
    INC     HL
    DJNZ    GSP_LOOP

; On split-RAM machines where Page 0 & Page 1 share SLOT1_FND (e.g. Yamaha AX200),
; MSXB2R-based 48K ROMs switch Page 0 to read Block 1 via LD A,(0xC01D); LD H,0x00; CALL ...
; Point that read to (0xC01C) (Page 1 slot) instead of (0xC01D) (Page 2 slot).
GSP_CHK_PG0:
    LD      A,(SLOT0_FND)
    LD      HL,#SLOT1_FND
    CP      (HL)
    RET     NZ
    LD      HL,#0x4000
    LD      BC,#0x3FFB
GSP_P0_LOOP:
    LD      A,(HL)
    CP      #0x3A
    JR      NZ,GSP_P0_NEXT
    PUSH    HL
    INC     HL
    LD      A,(HL)
    CP      #0x1D
    JR      NZ,GSP_P0_POP
    INC     HL
    LD      A,(HL)
    CP      #0xC0
    JR      NZ,GSP_P0_POP
    INC     HL
    LD      A,(HL)
    CP      #0x26
    JR      NZ,GSP_P0_POP
    INC     HL
    LD      A,(HL)
    OR      A
    JR      NZ,GSP_P0_POP
    INC     HL
    LD      A,(HL)
    CP      #0xCD
    JR      NZ,GSP_P0_POP
    POP     HL
    PUSH    HL
    INC     HL
    LD      (HL),#0x1C
GSP_P0_POP:
    POP     HL
GSP_P0_NEXT:
    INC     HL
    DEC     BC
    LD      A,B
    OR      C
    JR      NZ,GSP_P0_LOOP
    RET

START6:
    LD      HL,(EXECA)
    PUSH    HL
    LD      HL,(ENDA)
    LD      DE,(STARTA)
    OR      A
    SBC     HL,DE
    LD      B,H
    LD      C,L
    LD      HL,#ROMCODE
    LDIR
    POP     HL
    LD      A,H
    CP      #0x40
    JR      NC,S6_GO
    LD      A,(SLOT0_FND)
    AND     #0x03
    LD      B,A
    IN      A,(0x0A8)
    AND     #0x0FC
    OR      B
    OUT     (0x0A8),A
    EI
    JP      (HL)
S6_GO:
    EX      DE,HL
    LD      HL,#S6_RET
    LD      BC,#START6
    OR      A
    SBC     HL,BC
    LD      BC,#0x0F560
    ADD     HL,BC
    PUSH    HL
    EX      DE,HL
    EI
    JP      (HL)
S6_RET:
    DI
    LD      A,(#0x0FEDA)
    CP      #0x0C9
    JR      Z,S6_BASIC
    CP      #0x0F7
    JR      NZ,S6_CALL_STKE
    LD      A,(#0x0FCC1)
    LD      H,#0x40
    CALL    0x0024
S6_CALL_STKE:
    EI
    CALL    0x0FEDA
    DI
S6_BASIC:
    LD      A,(#0x0FCC1)
    LD      H,#0x40
    CALL    0x0024
    EI
    RET
    NOP

ROMCODE:
