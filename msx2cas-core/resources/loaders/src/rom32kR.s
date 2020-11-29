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
    EI
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
    LD      DE,#CASERR
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
    RET

START5:
    DI
    LD      A,(0x0FFFF)
    CPL
    LD      C,A
    AND     #0x0F0
    LD      B,A
    LD      A,C
    RRCA
    RRCA
    RRCA
    RRCA
    AND     #15
    OR      B
    LD      (0x0FFFF),A
    IN      A,(0x0A8)
    AND     #0x0F0
    LD      B,A
    RRCA
    RRCA
    RRCA
    RRCA
    AND     #15
    OR      B
    PUSH    AF
    OUT     (0x0A8),A

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
    POP     AF
    AND     #0x0FC
    OUT     (0x0A8),A
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
    LD      A,#00         ; +9 bytes
    LD      (HL),A
    INC     HL
    LD      A,#00        ; +13 bytes
    LD      (HL),A
    LD      HL,#0x0000    ; +16 bytes
    PUSH    HL
    LD      A,#00        ; +20 bytes
    LD      (HL),A
    INC     HL
    LD      A,#00        ; +24 bytes
    LD      (HL),A
    INC     HL
    LD      A,#00          ; +28 bytes
    LD      (HL),A
    POP     HL
    JP      (HL)
    NOP

ROMCODE:
