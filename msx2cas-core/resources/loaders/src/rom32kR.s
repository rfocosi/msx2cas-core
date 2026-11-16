; BLOAD MODULE FOR 32KB ROMS PART 2 WITH RESET (FOR CASLINK3 PROJECT)
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

START5:
    DI
    LD      HL,#0x4000
    LD      DE,#0x7FFE
    CALL    FINDRAM
    JP      C,RAMERR
    LD      (SLOT1_FND),A
    LD      H,#0x40
    CALL    0x0024        ; ENASLT: map Page 1 to detected RAM slot
    DI
    LD      H,#0x40
    LD      A,#0xFD
    LD      E,#2
    CALL    UNALIAS_PG
    CALL    GUARD_SPLIT_P2

START6:
    LD      HL,(STARTA)
    LD      A,H
    CP      #0x80
    JR      C,START7
    LD      HL,#ROMCODE
    LD      DE,#START8+5
    LD      A,(HL)
    LD      (DE),A        ; transfer byte from 0x8000 to patcher
    INC     HL
    LD      DE,#START8+9
    LD      A,(HL)
    LD      (DE),A        ; transfer byte from 0x8001 to patcher
    INC     HL
    LD      DE,#START8+13
    LD      A,(HL)
    LD      (DE),A        ; transfer byte from 0x8002 to patcher
    LD      HL,(EXECA)
    PUSH    HL
    LD      (START8+16),HL    ; transfer start address to patcher
    LD      A,(HL)
    LD      DE,#START8+20
    LD      (DE),A        ; transfer 1st byte from EPA
    INC     HL
    LD      A,(HL)
    LD      DE,#START8+24
    LD      (DE),A        ; transfer 2nd byte from EPA
    INC     HL
    LD      A,(HL)
    LD      DE,#START8+28
    LD      (DE),A        ; transfer 3rd byte from EPA
    POP     HL
    LD      A,#0x0CD
    LD      (HL),A        ; place call opcode at EPA
    INC     HL
    PUSH    HL
    LD      HL,#ROMCODE+4
    LD      DE,#0x8000
    SCF
    CCF
    SBC     HL,DE        ; offset for finding the end of ROM+4
    LD      DE,(ENDA)
    SCF
    CCF
    ADC     HL,DE
    EX      DE,HL
    PUSH    DE
    LD      HL,#START8
    LD      BC,#33
    LDIR            ; transfer patcher to the end of ROM+4
    LD      HL,(ENDA)
    LD      DE,#37
    SCF
    CCF
    ADC     HL,DE
    LD      (ENDA),HL    ; adjust ROM's end address with patcher size
    POP     DE
    POP     HL
    LD      A,E
    LD      (HL),A        ; save the low byte of call address
    INC     HL
    LD      A,D
    LD      (HL),A        ; save the high byte of call address
    LD      HL,#START7
    LD      DE,#START8+1
    EX      DE,HL
    SCF
    CCF
    SBC     HL,DE
    LD      B,H
    LD      C,L
    LD      HL,#START7
    LD      DE,#0x0F560
    PUSH    DE
    LDIR
    RET

START7:
    LD      HL,(STARTA)
    LD      DE,(ENDA)
    EX      DE,HL
    SCF
    CCF
    SBC     HL,DE
    LD      B,H
    LD      C,L
    LD      HL,#ROMCODE
    LD      DE,(STARTA)
    LDIR
    RST     0x30
    .DW     0
    .DW     0
    NOP

START8:
    DI
    LD      HL,#0x8000
    LD      A,#00        ; +5 bytes
    LD      (HL),A
    INC     HL
    LD      A,#00        ; +9 bytes
    LD      (HL),A
    INC     HL
    LD      A,#00        ; +13 bytes
    LD      (HL),A
    LD      HL,#0x0000   ; +16 bytes
    PUSH    HL
    LD      A,#00        ; +20 bytes
    LD      (HL),A
    INC     HL
    LD      A,#00        ; +24 bytes
    LD      (HL),A
    INC     HL
    LD      A,#00        ; +28 bytes
    LD      (HL),A
    POP     HL
    JP      (HL)
    NOP

; On split-RAM machines where Page 1 RAM lacks Page 2 RAM, a 32K ROM with Block 2 at 0x8000
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
    RET

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
    RET

ROMCODE:
