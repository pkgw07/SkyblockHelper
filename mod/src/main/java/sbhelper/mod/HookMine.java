package sbhelper.mod;

/** 낚싯줄 그리기: 이 찌가 내 것인지 (렌더 상태에 붙여 둔다) */
public interface HookMine {
    boolean sbh$mine();
    void sbh$setMine(boolean mine);
}
