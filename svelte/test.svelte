<script>
    let count = $state(0);
    const double = $derived(count * 2);

    function increment() {
        count += 1;
    }
</script>

<button onclick={increment}>
    تعداد: {count} | دوبرابر: {double}
</button>

<style>
    button {
        padding: 0.6rem 1rem;
        border-radius: 8px;
        border: 1px solid #ccc;
        cursor: pointer;
    }
</style>
