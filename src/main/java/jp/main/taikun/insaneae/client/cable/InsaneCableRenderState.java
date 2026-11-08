package jp.main.taikun.insaneae.client.cable;

/**
 * AE2の {@code CableBusRenderState} に「どのModのケーブルか」を持たせるための口。
 *
 * <p>AE2のレンダーステートは、ケーブル種別、色、チャンネル数しか持たないので、
 * このままではこちらの2本とAE2のスマートケーブルが区別できない。
 * {@code CableBusRenderStateMixin} がこの口を実装し、
 * {@code equals} / {@code hashCode} にも種類を混ぜる
 * レンダーステートは板のキャッシュの鍵でもあるため。</p>
 */
public interface InsaneCableRenderState {

    /** この板を描くケーブルの種類。AE2のケーブルならnull。 */
    InsaneCableSprites.Kind insaneae$getCableKind();

    void insaneae$setCableKind(InsaneCableSprites.Kind kind);
}
